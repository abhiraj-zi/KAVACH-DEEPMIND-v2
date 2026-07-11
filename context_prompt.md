# Project Kavach — Backend Build Context

> Working context for the agentic backend. Goal: **prototype demo only** for a
> hackathon. Focus is the **online "Ghost Operator" branch** — Gemini Flash +
> Computer Use, orchestrated, streaming live events to the existing frontend.
> Act fast, keep it demo-robust (every agent has a graceful fallback).

---

## 1. What we're building

The backend "brain" behind the already-built Kavach frontend. On a **Code Red**
(safeword / button), an **Orchestrator** fans out sub-agents in parallel and
streams their live progress to the phone UI:

- **Orchestrator** (`Antigravity`) — classifies Code Red, spawns sub-agents.
- **Action Agent** (`Computer Use`) — **Gemini Computer Use + Playwright** drives
  a real Chromium browser: opens Google Maps, resolves GPS location, routes to
  the **nearest police station / safe zone**. Zero-UI.
- **Comms Agent** (`Live Voice`) — **real Twilio** SMS + voice call to emergency
  contacts, with a calm synthesized crisis message. Falls back to simulated.
- **Verification Agent** (`Omni`) — Gemini Flash ambient context / threat level.

Offline "Dark Survival" (Gemma 4) branch is **out of scope** for this build.

## 2. Decisions locked (from user)

- **Scope:** Full online branch (Orchestrator + Verification + Action + Comms).
- **Stack:** Python + Google ADK where it fits; `google-genai` directly for the
  Computer Use loop (ADK doesn't wrap the screenshot/tool loop cleanly).
- **Transport:** **SSE** — because the existing frontend already speaks it
  (`EventSource('/session/1/events')`). (We picked SSE over WebSocket to match
  the contract that's already there, not for its own sake.)
- **Comms:** Real **Twilio** (free trial → only *verified* numbers) with an
  automatic **simulated fallback** if creds missing / trial limits hit.
- **Computer Use target:** **Real** — Google Maps for location + nearest police
  station. SMS/call go through Twilio (more reliable on stage than browsing a
  telephony site). Fallback still yields a real, openable Maps link.

## 3. The frontend contract (ALREADY DEFINED — we build to it)

Discovered in `kavach/server.py` + `kavach/web/app.js`. Do not change it.

- `POST /trigger` — body `{action, mode, source}` where
  `action ∈ {"code_red","mode_switch","resolve"}`. Returns `{status, mode}`.
- `GET /session/{id}/events` — **Server-Sent Events**. Each event JSON:
  ```json
  {"stage":"Action","agent":"Computer Use",
   "message":"Routing to nearest police station...",
   "status":"active","mode":"online"}
  ```
- `agent` MUST be one of the UI's icon-mapped names:
  - `"Antigravity"` → orchestrator (hub icon)
  - `"Computer Use"` → action agent (explore icon)
  - `"Live Voice"` → comms agent (phone_in_talk icon, green)
  - `"Omni"` (or other) → verification / default (info icon)
- `status == "failed"` renders red; anything else neutral/active.
- Server serves `kavach/web/index.html` at `/` and static at `/web`.
- Web app has a local **mock sequence** fallback if the backend is offline.

### Frontend surfaces present
- `kavach/web/` — HTML/JS demo UI (the SSE consumer; this is the primary demo UI).
- `kavach/app/` — Android (Jetpack Compose) app. Currently **fully self-contained
  mock**: it does TTS whisper nav + mock SMS logs locally, and only calls Gemini
  directly for the "Scribe" incident report (`GeminiClient.kt`,
  model `gemini-3.5-flash`). It does **not** yet hit `/trigger` or the SSE stream
  — wiring it to the backend is an optional follow-up (Android code change).

## 4. Environment / models

- Repo root: `/Users/abhi/Desktop/KAVACH-DEEPMIND`. Git repo, branch `main`.
- **API key:** `.env` at repo root has `GEMINI_AI_KEY` (note: `_AI_`, not the
  Android app's `GEMINI_API_KEY`). Length 53. Verified working.
- **Models verified live on this key (2026-07):**
  - `gemini-3.5-flash` ✓ — orchestrator + verification (also Android Scribe)
  - `gemini-2.5-computer-use-preview-10-2025` ✓ — Computer Use
  - `gemini-2.5-flash-native-audio-latest` — live bidi audio (Comms, future)
  - `gemini-omni-flash-preview` — omni audio context
  - `gemma-4-31b-it` — offline branch (out of scope)
- **Python:** 3.12.1, venv at `.venv/` (repo root).
- **Installed:** google-adk 2.4.0, google-genai 2.11.0, playwright 1.61.0,
  twilio 9.10.9, fastapi, uvicorn, python-dotenv, pydantic.
- **Chromium for Playwright: NOT yet installed** — `playwright install chromium`
  was rejected by user; needs to be run before Computer Use works.

### Gemini Computer Use API (confirmed in installed SDK)
```python
from google import genai
from google.genai import types
tool = types.Tool(computer_use=types.ComputerUse(
    environment=types.Environment.ENVIRONMENT_BROWSER))
cfg  = types.GenerateContentConfig(tools=[tool])
# loop: generate_content -> candidate.content has function_call parts ->
# execute action in Playwright -> reply with:
types.Part(function_response=types.FunctionResponse(
    name=fc.name, response={"url": page.url},
    parts=[types.FunctionResponsePart(inline_data=types.FunctionResponseBlob(
        mime_type="image/png", data=png_bytes))]))
```
Predefined browser actions handled: open_web_browser, navigate, go_back/forward,
search, click_at, hover_at, type_text_at, key_combination, scroll_document,
scroll_at, wait_5_seconds. Coords are normalized 0–1000 → scale to viewport.

## 5. Code written so far (repo-root `backend/` package — engine)

- `backend/config.py` — loads root `.env`, model IDs, Twilio config, demo knobs
  (SAFEWORD, USER_NAME, DEFAULT_LAT/LNG, BROWSER_HEADLESS). `require_api_key()`.
- `backend/events.py` — `EventBus` (async queue) emitting the SSE shape above;
  `emit()`, `emit_threadsafe()` (for Twilio/Playwright worker threads),
  `stream()`, `close()`. Agent-name constants.
- `backend/agents/comms.py` — `run_comms_agent(bus, location_text, maps_url)`:
  real Twilio SMS + voice (TwiML `<Say>`), graceful `_simulate()` fallback.
  *(Written against the OLD event API — needs updating to new `bus.emit`
  signature `emit(stage, agent, message, status)`.)*
- `backend/agents/action.py` — `run_action_agent(bus, lat, lng)`: full Computer
  Use loop driving Maps to nearest police station; bounded (MAX_STEPS=14,
  120s timeout); deterministic fallback returns a real Maps search URL.

## 6. TODO (remaining)

1. Install chromium for Playwright (was rejected — re-confirm with user).
2. Update `comms.py` to the new `bus.emit(stage, agent, message, status)` API.
3. Test the Computer Use loop in isolation against the real API, fix as needed.
4. `backend/agents/verification.py` — Gemini Flash threat context, streams.
5. `backend/orchestrator.py` — classify Code Red, fan out agents concurrently,
   emit orchestrator events; collect Action result → hand `maps_url` to Comms.
6. Wire the real orchestrator into an SSE server matching the contract
   (`/trigger` + `/session/{id}/events`), serving `kavach/web`. Likely replace
   the mock `event_generator()` in `kavach/server.py`.
7. End-to-end smoke test with the web UI; write README with run instructions.

## 7. Fallback philosophy (demo must never hard-fail)

Every agent degrades gracefully: Computer Use timeout → real Maps link;
Twilio missing/failed → simulated SMS+call events; no contacts → simulated.
The UI already has its own offline mock sequence as a last resort.

## 8. Notes / gotchas

- Shell working directory has drifted in this session — **always use absolute
  paths** in Bash calls.
- Two different env var names in play: backend uses `GEMINI_AI_KEY` (root
  `.env`); Android app expects `GEMINI_API_KEY` (its own `.env`).
- Twilio trial can only message/call **verified** numbers — verify emergency
  contacts in the Twilio console first. Config via env: `TWILIO_ACCOUNT_SID`,
  `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`, `EMERGENCY_CONTACTS` (comma-sep).
