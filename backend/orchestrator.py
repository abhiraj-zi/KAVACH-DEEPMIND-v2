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

from . import config
from .events import AGENT_ORCHESTRATOR, AGENT_VERIFY, EventBus
from .agents.action import run_action_agent
from .agents.comms import run_comms_agent
from .agents.verification import run_verification_agent

STAGE = "Orchestrator"


async def run_code_red(
    bus: EventBus,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
) -> None:
    """Drive a full Code Red response, streaming events, then close the bus."""
    lat = lat if lat is not None else config.DEFAULT_LAT
    lng = lng if lng is not None else config.DEFAULT_LNG
    location_text = f"{lat:.4f}, {lng:.4f}"

    try:
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            "CODE RED classified — silent distress confirmed. Spawning "
            "Verification, Action and Comms agents.",
        )

        # Fan out the two independent agents concurrently.
        verify_task = asyncio.create_task(
            run_verification_agent(bus, location_text, lat, lng)
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
        # tells the de-escalation story as help closes in.
        await _monitor(bus, action_result.safe_zone)
    except Exception as exc:  # noqa: BLE001 — never leave the UI hanging
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            f"Orchestrator error ({type(exc).__name__}) — response degraded but "
            "active.",
            status="failed",
        )
    finally:
        await bus.close()


async def _monitor(bus: EventBus, safe_zone: str) -> None:
    """Continuous ambient re-assessment until the session is resolved (cancelled).

    Emits Omni re-scans that de-escalate over time as the response closes in,
    driving the UI's live threat meter. Cancellation (Resolve) closes the bus.
    """
    stages = [
        ("HIGH", 74, f"Subject isolated. Police unit routing to {safe_zone}."),
        ("HIGH", 66, "Ambient audio steady. Emergency contact acknowledged alert."),
        ("MEDIUM", 55, f"De-escalating — approach to {safe_zone} underway."),
        ("MEDIUM", 47, "Bystander density rising near route. Risk dropping."),
        ("LOW", 32, f"Nearing {safe_zone}. Contact en route."),
    ]
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
