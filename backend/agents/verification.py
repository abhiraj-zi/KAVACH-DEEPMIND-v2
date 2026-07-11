"""Verification Agent — ambient threat context via Gemini Flash.

On a Code Red the orchestrator spawns this agent to assess the situation and
produce a short threat classification + confidence that the UI streams live.

Two analysis paths, best-first:
  1. VOICE — if an ambient audio clip is supplied, send the real audio to a
     fast Gemini Flash (``MODEL_VOICE_ANALYSIS``) and classify what it hears.
  2. TEXT — otherwise classify from the GPS location context alone.
If the model/key is unavailable either path degrades to a deterministic
HIGH-threat assessment so the demo never hard-fails.

Streams to the UI via the shared EventBus using the canonical event shape:
    bus.emit(stage="Verification", agent="Omni", message=..., status=...)
"""
from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass
from typing import Optional

from .. import config, local_llm
from ..events import AGENT_GEMMA, AGENT_VERIFY, EventBus

STAGE = "Verification"
STEP_TIMEOUT_S = 20

_LOCAL_SYSTEM = (
    "You are Omni running fully ON-DEVICE (local Gemma) because the phone has "
    "NO internet. You are the ambient safety analyst inside Kavach, a "
    "personal-safety system. Be decisive and calm. Output JSON only."
)

# Default container for browser/mic recordings. WEBM/Opus is what a browser
# MediaRecorder emits; WAV/OGG/MP3 also work — pass the real mime through.
DEFAULT_AUDIO_MIME = "audio/webm"

_TEXT_PROMPT = (
    "You are Omni, the ambient safety analyst inside Kavach, a personal-safety "
    "system. A user has just triggered a silent 'Code Red' distress signal from "
    "GPS location {location_text}. Assess the likely threat level for a person "
    "alone in this situation. Respond ONLY with compact JSON: "
    '{{"threat_level":"HIGH|MEDIUM|LOW","confidence":0-100,'
    '"context":"one short calm sentence of situational context"}}'
)

_AUDIO_PROMPT = (
    "You are Omni, the ambient safety analyst inside Kavach, a personal-safety "
    "system. The attached audio is a short ambient recording from the user's "
    "device around a possible distress event near {location_text}. Listen for "
    "signs of danger: raised, panicked or pleading voices, calls for help, "
    "threats, a struggle, breaking glass, a vehicle, or an eerily isolated/quiet "
    "space. Judge the threat level for a person alone here. Respond ONLY with "
    "compact JSON: "
    '{{"threat_level":"HIGH|MEDIUM|LOW","confidence":0-100,'
    '"context":"one short calm sentence describing what you heard"}}'
)


@dataclass
class VerificationResult:
    threat_level: str
    confidence: int
    context: str
    simulated: bool
    modality: str = "text"  # "voice" when derived from real audio


async def run_verification_agent(
    bus: EventBus,
    location_text: str,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
    audio: Optional[bytes] = None,
    audio_mime: str = DEFAULT_AUDIO_MIME,
    offline: bool = False,
) -> VerificationResult:
    heard = f" — analysing {len(audio)} bytes of ambient audio" if audio else ""
    await bus.emit(
        STAGE, AGENT_VERIFY,
        f"Verification Agent spawned — assessing ambient threat context{heard}.",
    )

    # No internet → go straight to the on-device Gemma brain (skip Gemini).
    if offline or not config.GEMINI_API_KEY:
        why = "no network — on-device only" if offline else "GEMINI_AI_KEY missing"
        local = await _try_local(bus, location_text)
        return local if local else await _simulate(bus, why)

    try:
        if audio:
            result = await asyncio.wait_for(
                analyze_ambient_audio(audio, audio_mime, location_text),
                timeout=STEP_TIMEOUT_S,
            )
        else:
            result = await asyncio.wait_for(
                _classify_text(location_text), timeout=STEP_TIMEOUT_S
            )
    except Exception as exc:  # noqa: BLE001 — demo must survive any model error
        # Cloud model failed (e.g. Wi-Fi dropped mid-call). Try the on-device
        # Gemma brain for a *real* assessment before the deterministic stub.
        local = await _try_local(bus, location_text)
        if local:
            return local
        return await _simulate(bus, f"{type(exc).__name__}: {exc}")

    tag = "voice" if result.modality == "voice" else "context"
    await bus.emit(
        STAGE, AGENT_VERIFY,
        f"Threat {result.threat_level} ({result.confidence}% conf, {tag}) — "
        f"{result.context}",
    )
    return result


async def analyze_ambient_audio(
    audio: bytes,
    mime_type: str = DEFAULT_AUDIO_MIME,
    location_text: str = "an unknown location",
) -> VerificationResult:
    """Send real ambient audio to fast Gemini Flash and classify the threat.

    Reusable outside the EventBus (e.g. the Sentinel's ambient-audio confirm).
    Raises on model/transport errors — callers decide how to degrade.
    """
    from google import genai
    from google.genai import types

    client = genai.Client(api_key=config.require_api_key())
    prompt = _AUDIO_PROMPT.format(location_text=location_text)
    resp = await client.aio.models.generate_content(
        model=config.MODEL_VOICE_ANALYSIS,
        contents=[
            prompt,
            types.Part.from_bytes(data=audio, mime_type=mime_type),
        ],
    )
    return _result_from_reply(resp.text, modality="voice")


async def _classify_text(location_text: str) -> VerificationResult:
    from google import genai

    client = genai.Client(api_key=config.require_api_key())
    prompt = _TEXT_PROMPT.format(location_text=location_text)
    resp = await client.aio.models.generate_content(
        model=config.MODEL_VERIFICATION,
        contents=prompt,
    )
    return _result_from_reply(resp.text, modality="text")


def _result_from_reply(text: Optional[str], modality: str) -> VerificationResult:
    data = _parse_json((text or "").strip())
    return VerificationResult(
        threat_level=str(data.get("threat_level", "HIGH")).upper(),
        confidence=int(data.get("confidence", 85)),
        context=str(
            data.get("context", "Situation escalating; response dispatched.")
        ),
        simulated=False,
        modality=modality,
    )


def _parse_json(text: str) -> dict:
    """Extract the first JSON object from a model reply (handles code fences)."""
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            pass
    return {}


async def _try_local(
    bus: EventBus, location_text: str
) -> Optional[VerificationResult]:
    """Assess threat with the local Gemma model. None if it's unavailable."""
    await bus.emit(
        STAGE, AGENT_GEMMA,
        "Cloud unreachable — engaging on-device Gemma for threat assessment.",
    )
    try:
        prompt = _TEXT_PROMPT.format(location_text=location_text)
        text = await asyncio.wait_for(
            local_llm.chat(prompt, system=_LOCAL_SYSTEM),
            timeout=config.LOCAL_LLM_TIMEOUT_S + 2,
        )
    except Exception as exc:  # noqa: BLE001 — fall through to deterministic stub
        await bus.emit(
            STAGE, AGENT_GEMMA,
            f"On-device Gemma unavailable ({type(exc).__name__}) — using "
            "deterministic assessment.",
        )
        return None

    result = _result_from_reply(text, modality="on-device")
    await bus.emit(
        STAGE, AGENT_GEMMA,
        f"On-device threat {result.threat_level} ({result.confidence}% conf) — "
        f"{result.context} [{local_llm.resolved_model_name()}]",
    )
    return result


async def _simulate(bus: EventBus, reason: str) -> VerificationResult:
    await bus.emit(
        STAGE, AGENT_VERIFY,
        "Threat HIGH (90% conf) — user isolated; autonomous response engaged. "
        f"(offline assessment: {reason})",
    )
    return VerificationResult(
        threat_level="HIGH",
        confidence=90,
        context="User isolated; autonomous response engaged.",
        simulated=True,
        modality="text",
    )
