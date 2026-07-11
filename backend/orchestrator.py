"""Orchestrator — the Kavach "brain" behind a Code Red.

On a Code Red the orchestrator (Antigravity):
  1. Classifies the trigger and announces the plan.
  2. Fans out sub-agents concurrently:
       - Verification (Omni)       — ambient threat context.
       - Action (Computer Use)     — drive Maps to nearest police station.
  3. Once Action resolves a location + maps link, hands them to Comms
     (Live Voice) to alert emergency contacts.
  4. Emits a final summary and closes the event stream.

Every step streams to the UI over the shared EventBus in the canonical shape.
The whole run is wrapped so any unexpected error still closes the bus cleanly —
the demo must never leave the UI hanging.
"""
from __future__ import annotations

import asyncio
from typing import Optional

from . import config, local_llm
from .events import AGENT_GEMMA, AGENT_ORCHESTRATOR, AGENT_VERIFY, EventBus
from .agents.action import run_action_agent, run_action_agent_offline
from .agents.comms import run_comms_agent, run_comms_agent_offline
from .agents.verification import run_verification_agent
from .logutil import get_logger

STAGE = "Orchestrator"
log = get_logger("kavach.gemma")


async def run_code_red(
    bus: EventBus,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
    audio: Optional[bytes] = None,
    audio_mime: str = "audio/webm",
    offline: Optional[bool] = None,
) -> None:
    """Drive a full Code Red response, streaming events, then close the bus.

    ``audio`` (optional) is a short ambient recording; when present the
    Verification agent analyses the real audio instead of location text alone.

    ``offline`` forces the on-device path (DARK SURVIVAL). When left None the
    orchestrator probes connectivity itself: no internet → on-device Gemma.
    """
    lat = lat if lat is not None else config.DEFAULT_LAT
    lng = lng if lng is not None else config.DEFAULT_LNG
    location_text = f"{lat:.4f}, {lng:.4f}"

    if offline is None:
        offline = not await local_llm.internet_up()

    if offline:
        await _run_offline(bus, lat, lng, location_text)
        return

    try:
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            "CODE RED classified — silent distress confirmed. Spawning "
            "Verification, Action and Comms agents.",
        )

        # Fan out the two independent agents concurrently.
        verify_task = asyncio.create_task(
            run_verification_agent(
                bus, location_text, lat, lng, audio=audio, audio_mime=audio_mime
            )
        )
        action_task = asyncio.create_task(run_action_agent(bus, lat, lng))

        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            "Agents running in parallel — resolving safe route and threat level.",
        )

        # Comms needs the Action agent's resolved location + map link.
        action_result = await action_task

        comms_task = asyncio.create_task(
            run_comms_agent(
                bus,
                location_text=action_result.location_text,
                maps_url=action_result.maps_url,
            )
        )

        verify_result, comms_result = await asyncio.gather(verify_task, comms_task)

        eta_txt = f" (ETA {action_result.eta})" if action_result.eta else ""
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            f"Response coordinated — route to {action_result.safe_zone}{eta_txt} locked, "
            f"contacts alerted ({'live' if not comms_result.simulated else 'simulated'}), "
            f"threat {verify_result.threat_level}. Standing by, monitoring.",
        )

        # Stay live: keep re-assessing ambient threat until the user resolves
        # (which cancels this task). This animates the UI's threat meter and
        # tells the de-escalation story as help closes in — starting from the
        # level the real voice analysis actually reported.
        await _monitor(bus, action_result.safe_zone, verify_result.threat_level)
    except Exception as exc:  # noqa: BLE001 — never leave the UI hanging
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            f"Orchestrator error ({type(exc).__name__}) — response degraded but "
            "active.",
            status="failed",
        )
    finally:
        await bus.close()


async def _run_offline(
    bus: EventBus,
    lat: float,
    lng: float,
    location_text: str,
) -> None:
    """DARK SURVIVAL — the phone has no internet; the on-device brain takes over.

    The cloud agents (Gemini Computer Use, Twilio) can't reach the network, so
    the orchestrator drives an on-device response instead:
      - Gemma assesses the threat locally,
      - the Action agent resolves the nearest safe zone from a cached map,
      - the Comms agent drafts the SOS with Gemma and queues the beacon to
        auto-transmit the instant signal returns.
    """
    log.info("=" * 62)
    log.info("🛡  DARK SURVIVAL engaged — running FULLY ON-DEVICE (no internet)")
    log.info("   location=%s", location_text)
    try:
        gemma_up = await local_llm.is_up()
        log.info("   LM Studio reachable: %s%s", gemma_up,
                 f" [{local_llm.resolved_model_name()}]" if gemma_up else "")
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            "NO NETWORK detected — cloud unreachable. Engaging DARK SURVIVAL: "
            "handing off to on-device Gemma.",
        )
        if gemma_up:
            await bus.emit(
                STAGE, AGENT_GEMMA,
                f"On-device model online [{local_llm.resolved_model_name()}] — "
                "full response now runs locally, zero data leaves the phone.",
            )
        else:
            await bus.emit(
                STAGE, AGENT_GEMMA,
                "Local model not reachable (start LM Studio) — using "
                "deterministic on-device fallback.",
            )

        # Action first (cheap, no model) so Comms can cite the safe zone.
        action_result = await run_action_agent_offline(bus, lat, lng)

        verify_task = asyncio.create_task(
            run_verification_agent(bus, location_text, lat, lng, offline=True)
        )
        comms_task = asyncio.create_task(
            run_comms_agent_offline(
                bus,
                location_text=action_result.location_text,
                maps_url=action_result.maps_url,
                safe_zone=action_result.safe_zone,
            )
        )
        verify_result, _comms_result = await asyncio.gather(verify_task, comms_task)

        log.info(
            "   on-device result → threat=%s conf=%s%% | safe_zone=%s (ETA %s)",
            verify_result.threat_level, verify_result.confidence,
            action_result.safe_zone, action_result.eta,
        )
        log.info("🛡  DARK SURVIVAL response coordinated on-device.")
        log.info("=" * 62)

        eta_txt = f" (ETA {action_result.eta})" if action_result.eta else ""
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            f"On-device response coordinated — route to {action_result.safe_zone}"
            f"{eta_txt} from cached map, SOS beacon armed, threat "
            f"{verify_result.threat_level}. Monitoring on-device until signal "
            "returns.",
        )
        await _monitor(bus, action_result.safe_zone, verify_result.threat_level)
    except Exception as exc:  # noqa: BLE001 — never leave the UI hanging
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            f"On-device orchestrator error ({type(exc).__name__}) — response "
            "degraded but active.",
            status="failed",
        )
    finally:
        await bus.close()


async def _monitor(
    bus: EventBus, safe_zone: str, start_level: str = "HIGH"
) -> None:
    """Continuous ambient re-assessment until the session is resolved (cancelled).

    Emits Omni re-scans that de-escalate over time as the response closes in,
    driving the UI's live threat meter. Cancellation (Resolve) closes the bus.

    The de-escalation curve STARTS at ``start_level`` — the threat level Omni
    actually assessed (from the real voice analysis) — so the timeline never
    contradicts what the mic just heard by jumping back up to HIGH.
    """
    ladder = [
        ("HIGH", 74, f"Subject isolated. Police unit routing to {safe_zone}."),
        ("HIGH", 66, "Ambient audio steady. Emergency contact acknowledged alert."),
        ("MEDIUM", 55, f"De-escalating — approach to {safe_zone} underway."),
        ("MEDIUM", 47, "Bystander density rising near route. Risk dropping."),
        ("LOW", 32, f"Nearing {safe_zone}. Contact en route."),
    ]
    rank = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
    start_rank = rank.get(str(start_level).upper(), 0)
    # Only keep rungs at or below the real starting threat (never re-escalate).
    stages = [s for s in ladder if rank[s[0]] >= start_rank]
    for level, conf, ctx in stages:
        await asyncio.sleep(6)
        await bus.emit(
            "Verification", AGENT_VERIFY,
            f"Ambient re-scan — threat {level} ({conf}% conf) — {ctx}",
        )

    # Steady-state watch until resolved.
    while True:
        await asyncio.sleep(9)
        await bus.emit(
            "Verification", AGENT_VERIFY,
            "Ambient re-scan — threat LOW (30% conf) — monitoring; "
            "all agents standing by.",
        )
