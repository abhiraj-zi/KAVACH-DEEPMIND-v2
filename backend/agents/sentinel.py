"""Sentinel Agent — ambient distress listener (gemini-3.5-flash).

Captures rolling mic chunks, transcribes + classifies distress, streams the
transcript/threat to the UI as `Omni` events, and returns the triggering
result the moment distress (safeword or HIGH) is detected. Degrades to a
simulated trigger when audio/model is unavailable so the demo still advances.
"""
from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass
from typing import Optional

from .. import audio, config
from ..events import AGENT_VERIFY, EventBus

STAGE = "Monitoring"
_LEVELS = {"LOW": 0, "MED": 1, "HIGH": 2}

_PROMPT = (
    "You are Kavach's ambient safety sentinel. Given a short audio clip of a "
    "person's surroundings, transcribe any speech and judge whether the person "
    "is in danger or distress (threats, pleas for help, aggression, fear). "
    'Respond ONLY with compact JSON: {"transcript":"...","distress":true/false,'
    '"level":"LOW|MED|HIGH","reason":"short"}'
)


@dataclass
class SentinelResult:
    transcript: str
    distress: bool
    level: str
    reason: str


def _parse_classification(text: str) -> SentinelResult:
    start, end = text.find("{"), text.rfind("}")
    if start != -1 and end > start:
        try:
            d = json.loads(text[start : end + 1])
            return SentinelResult(
                transcript=str(d.get("transcript", "")),
                distress=bool(d.get("distress", False)),
                level=str(d.get("level", "LOW")).upper(),
                reason=str(d.get("reason", "")),
            )
        except json.JSONDecodeError:
            pass
    return SentinelResult("", False, "LOW", "unparseable")


def _is_distress(transcript: str, result: SentinelResult) -> bool:
    if config.SAFEWORD and config.SAFEWORD.lower() in (transcript or "").lower():
        return True
    trigger = _LEVELS.get(config.SENTINEL_LEVEL_TRIGGER, 2)
    return result.distress and _LEVELS.get(result.level, 0) >= trigger


async def _classify_chunk(pcm: bytes) -> SentinelResult:
    from google import genai
    from google.genai import types

    client = genai.Client(api_key=config.require_api_key())
    resp = await client.aio.models.generate_content(
        model=config.MODEL_SENTINEL,
        contents=[
            _PROMPT,
            types.Part(inline_data=types.Blob(
                mime_type="audio/pcm;rate=16000", data=pcm)),
        ],
    )
    return _parse_classification(resp.text or "")


async def _simulate(bus: EventBus) -> SentinelResult:
    await bus.emit(STAGE, AGENT_VERIFY, "Listening to surroundings… (simulated)")
    await asyncio.sleep(1.5)
    await bus.emit(
        STAGE, AGENT_VERIFY,
        f"Distress detected (simulated): \"{config.SAFEWORD}\".", "active",
    )
    return SentinelResult(config.SAFEWORD, True, "HIGH", "simulated")


async def run_sentinel(
    bus: EventBus, stop_event: asyncio.Event
) -> Optional[SentinelResult]:
    await bus.emit(STAGE, AGENT_VERIFY, "Sentinel active — monitoring for distress.")

    if config.SIMULATE_DISTRESS or not audio.AVAILABLE or not config.GEMINI_API_KEY:
        return await _simulate(bus)

    mic = audio.AudioIn()
    buf = bytearray()
    target = audio.MIC_RATE * 2 * config.SENTINEL_CHUNK_SECONDS  # 16-bit mono
    try:
        async for chunk in mic.chunks():
            if stop_event.is_set():
                return None
            buf.extend(chunk)
            if len(buf) < target:
                continue
            window, buf = bytes(buf), bytearray()
            try:
                result = await _classify_chunk(window)
            except Exception as exc:  # noqa: BLE001
                await bus.emit(STAGE, AGENT_VERIFY,
                               f"Sentinel classify error ({type(exc).__name__}).")
                continue
            if result.transcript:
                await bus.emit(STAGE, AGENT_VERIFY,
                               f"Heard: \"{result.transcript}\" · threat {result.level}")
            if _is_distress(result.transcript, result):
                await bus.emit(STAGE, AGENT_VERIFY,
                               f"DISTRESS confirmed — {result.reason}", "active")
                return result
    finally:
        mic.stop()
    return None
