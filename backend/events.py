"""Event contract streamed to the Kavach frontend over SSE.

The existing web UI (kavach/web/app.js) and Android app expect Server-Sent
Events on GET /session/{id}/events with this exact JSON shape:

    {"stage": "Action", "agent": "Computer Use",
     "message": "Routing to nearest police station...",
     "status": "active", "mode": "online"}

`agent` MUST be one of the names the UI maps to icons:
    "Antigravity"   -> orchestrator (hub icon)
    "Computer Use"  -> action agent (explore icon)
    "Live Voice"    -> comms agent (phone_in_talk icon)
    "Omni"          -> verification agent (info icon, no special mapping)

`status` == "failed" renders red. Anything else renders neutral/active.
"""
from __future__ import annotations

import asyncio
from typing import AsyncIterator


# Canonical agent display names (must match the UI icon map).
AGENT_ORCHESTRATOR = "Antigravity"
AGENT_ACTION = "Computer Use"
AGENT_COMMS = "Live Voice"
AGENT_VERIFY = "Omni"


class EventBus:
    """One bus per active Code Red session; every agent fans events in here."""

    def __init__(self, mode: str = "online") -> None:
        self.mode = mode
        self._queue: asyncio.Queue[dict | None] = asyncio.Queue()

    async def emit(
        self,
        stage: str,
        agent: str,
        message: str,
        status: str = "active",
    ) -> None:
        await self._queue.put(
            {
                "stage": stage,
                "agent": agent,
                "message": message,
                "status": status,
                "mode": self.mode,
            }
        )

    def emit_threadsafe(
        self,
        loop: asyncio.AbstractEventLoop,
        stage: str,
        agent: str,
        message: str,
        status: str = "active",
    ) -> None:
        """Emit from a worker thread (used by blocking SDK/Playwright code)."""
        asyncio.run_coroutine_threadsafe(
            self.emit(stage, agent, message, status), loop
        ).result()

    async def close(self) -> None:
        await self._queue.put(None)

    async def stream(self) -> AsyncIterator[dict]:
        """Yield events until the session is closed (sentinel None)."""
        while True:
            item = await self._queue.get()
            if item is None:
                return
            yield item
