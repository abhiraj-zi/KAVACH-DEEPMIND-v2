"""Kavach demo server — real agentic backend behind the existing web UI.

Serves kavach/web and speaks the frontend contract:
  POST /trigger              {action, mode, source}  -> {status, mode}
  GET  /session/{id}/events  Server-Sent Events (the orchestrator's live stream)

On a Code Red the orchestrator fans out sub-agents and streams their progress
here as SSE. Every agent degrades gracefully, so the demo never hard-fails;
if the backend itself is down the web app falls back to its own local mock.
"""
from __future__ import annotations

import asyncio
import base64
import json
import os
import sys

from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware

# Make the repo-root `backend` package importable regardless of CWD.
_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(_HERE)
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from backend.events import EventBus  # noqa: E402
from backend.orchestrator import run_code_red  # noqa: E402

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_WEB_DIR = os.path.join(_HERE, "web")


class Session:
    """One active Code Red: its event bus and the orchestrator task."""

    def __init__(self, bus: EventBus, task: asyncio.Task) -> None:
        self.bus = bus
        self.task = task


# Active sessions keyed by session id. Frontend hardcodes session "1".
_sessions: dict[str, Session] = {}


@app.get("/")
async def root():
    with open(os.path.join(_WEB_DIR, "index.html")) as f:
        return HTMLResponse(f.read())


@app.post("/trigger")
async def trigger(request: Request):
    try:
        data = await request.json()
    except Exception:  # noqa: BLE001 — tolerate empty/malformed bodies
        data = {}
    action = data.get("action")
    mode = data.get("mode", "online")
    print(f"Trigger received: action={action} mode={mode} source={data.get('source')}")

    if action == "code_red":
        # Offline mode (DARK SURVIVAL) forces the on-device Gemma path. Online
        # mode passes offline=None so the orchestrator probes connectivity and
        # auto-falls to Gemma if Wi-Fi is actually off.
        _start_session("1", data, offline=(mode == "offline"))
    elif action == "resolve":
        _end_session("1")
    # mode_switch is handled UI-side; just ack.

    return {"status": "ok", "mode": mode}


def _start_session(session_id: str, data: dict, offline: bool = False) -> None:
    _end_session(session_id)  # clear any stale session first
    bus = EventBus(mode="offline" if offline else "online")
    lat = data.get("lat")
    lng = data.get("lng")
    audio, audio_mime = _decode_audio(data)
    task = asyncio.create_task(
        run_code_red(
            bus, lat, lng, audio=audio, audio_mime=audio_mime,
            offline=True if offline else None,
        )
    )
    _sessions[session_id] = Session(bus, task)


def _decode_audio(data: dict):
    """Pull an optional base64 ambient clip off the trigger payload.

    Frontend/Android may send {"audio": "<base64>", "audio_mime": "audio/webm"}
    (a data-URI prefix like "data:audio/webm;base64," is tolerated). Returns
    (bytes | None, mime); never raises — a bad clip just skips voice analysis.
    """
    raw = data.get("audio")
    if not raw or not isinstance(raw, str):
        return None, "audio/webm"
    mime = data.get("audio_mime", "audio/webm")
    if raw.startswith("data:"):
        header, _, raw = raw.partition(",")
        if ";" in header and header[5:].split(";", 1)[0]:
            mime = header[5:].split(";", 1)[0]
    try:
        return base64.b64decode(raw, validate=False), mime
    except Exception:  # noqa: BLE001 — malformed clip -> fall back to text
        print("Trigger audio: could not decode base64 clip; ignoring.")
        return None, mime


def _end_session(session_id: str) -> None:
    session = _sessions.pop(session_id, None)
    if session and not session.task.done():
        session.task.cancel()


@app.get("/session/{session_id}/events")
async def sse_events(session_id: str):
    session = _sessions.get(session_id)

    async def generator():
        if session is None:
            # No active Code Red for this id — end the stream cleanly so the
            # frontend can fall back to its local mock if it wants to.
            yield "data: {}\n\n"
            return
        async for event in session.bus.stream():
            yield f"data: {json.dumps(event)}\n\n"

    return StreamingResponse(
        generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


# Serve web assets (app.js, etc.) at root so index.html's relative
# `src="app.js"` resolves. Mounted LAST so the API routes above take
# precedence over the catch-all static handler.
app.mount("/", StaticFiles(directory=_WEB_DIR, html=True), name="web")


if __name__ == "__main__":
    import uvicorn

    print("Starting Kavach demo server on http://localhost:8000")
    uvicorn.run(app, host="0.0.0.0", port=8000)
