"""Verification Agent — ambient threat context via Gemini Flash.

On a Code Red the orchestrator spawns this agent to assess the situation and
produce a short threat classification + confidence that the UI streams live.
It uses a fast Gemini Flash call; if the model/key is unavailable it degrades
to a deterministic HIGH-threat assessment so the demo never hard-fails.

Streams to the UI via the shared EventBus using the canonical event shape:
    bus.emit(stage="Verification", agent="Omni", message=..., status=...)
"""
from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass
from typing import Optional

from .. import config
from ..events import AGENT_VERIFY, EventBus

STAGE = "Verification"
STEP_TIMEOUT_S = 20

_PROMPT = (
    "You are Omni, the ambient safety analyst inside Kavach, a personal-safety "
    "system. A user has just triggered a silent 'Code Red' distress signal from "
    "GPS location {location_text}. Assess the likely threat level for a person "
    "alone in this situation. Respond ONLY with compact JSON: "
    '{{"threat_level":"HIGH|MEDIUM|LOW","confidence":0-100,'
    '"context":"one short calm sentence of situational context"}}'
)


@dataclass
class VerificationResult:
    threat_level: str
    confidence: int
    context: str
    simulated: bool


async def run_verification_agent(
    bus: EventBus,
    location_text: str,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
) -> VerificationResult:
    await bus.emit(
        STAGE, AGENT_VERIFY,
        "Verification Agent spawned — assessing ambient threat context.",
    )

    if not config.GEMINI_API_KEY:
        return await _simulate(bus, "GEMINI_AI_KEY missing")

    try:
        result = await asyncio.wait_for(
            _classify(location_text), timeout=STEP_TIMEOUT_S
        )
    except Exception as exc:  # noqa: BLE001 — demo must survive any model error
        return await _simulate(bus, f"{type(exc).__name__}: {exc}")

    await bus.emit(
        STAGE, AGENT_VERIFY,
        f"Threat {result.threat_level} ({result.confidence}% conf) — "
        f"{result.context}",
    )
    return result


async def _classify(location_text: str) -> VerificationResult:
    from google import genai

    client = genai.Client(api_key=config.require_api_key())
    prompt = _PROMPT.format(location_text=location_text)
    resp = await client.aio.models.generate_content(
        model=config.MODEL_VERIFICATION,
        contents=prompt,
    )
    text = (resp.text or "").strip()
    data = _parse_json(text)
    return VerificationResult(
        threat_level=str(data.get("threat_level", "HIGH")).upper(),
        confidence=int(data.get("confidence", 85)),
        context=str(data.get("context", "Situation escalating; response dispatched.")),
        simulated=False,
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
    )
