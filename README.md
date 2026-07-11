# Project Kavach — Agentic Backend

The "brain" behind the Kavach frontend. On a **Code Red** the **Orchestrator**
(`Antigravity`) fans out sub-agents in parallel and streams their live progress
to the phone UI over **Server-Sent Events**.

- **Orchestrator** — classifies the Code Red, spawns agents, coordinates result.
- **Action Agent** (`Computer Use`) — Gemini Computer Use + Playwright drives a
  real Chromium browser to route to the nearest police station.
- **Comms Agent** (`Live Voice`) — real Twilio SMS + voice call to emergency
  contacts (auto-falls back to simulated if creds/contacts missing).
- **Verification Agent** (`Omni`) — Gemini Flash ambient threat assessment.
- **On-device brain** (`Gemma`) — local Gemma via LM Studio. When there is **no
  internet**, Kavach flips to **DARK SURVIVAL**: Gemma runs the threat
  assessment and drafts the SOS entirely on-device.

Every agent degrades gracefully — the demo never hard-fails.

## Offline mode (DARK SURVIVAL — no internet)

The cloud agents (Gemini Computer Use, Twilio) need the network. When the phone
loses connectivity Kavach keeps working on-device:

- **Gemma (`Omni`)** — real threat assessment from a local Gemma model.
- **Action (`Computer Use`)** — nearest police station from a *cached offline
  map* (no browser), with a walking ETA.
- **Comms (`Live Voice`)** — Gemma drafts the SOS on-device and the beacon is
  **queued to auto-transmit the instant signal returns** (nothing is lost).

**How it triggers** — two ways, both real:
1. **Auto** — just turn Wi-Fi off and hit Code Red. The orchestrator probes
   connectivity (`internet_up()`); no internet → it routes to Gemma.
2. **Manual** — flip the UI's DARK SURVIVAL toggle to force the on-device path.

**Serving Gemma (LM Studio):** Developer tab → load your `gemma…e4b` model →
Start Server on port **1234**. Kavach auto-detects the loaded model id from
`/v1/models`; nothing to configure. If LM Studio isn't running, the offline path
still works via a deterministic fallback (it just won't be a real LLM).

Relevant `.env` knobs (all optional — sensible defaults):

```
KAVACH_LOCAL_LLM=true                        # enable on-device fallback
KAVACH_LOCAL_BASE_URL=http://localhost:1234/v1
KAVACH_LOCAL_MODEL=                          # blank = auto-detect Gemma
KAVACH_LOCAL_TIMEOUT_S=30
KAVACH_CONNECTIVITY_TIMEOUT_S=2.5            # how fast we declare "offline"
```

## Layout

```
backend/
  config.py            env + model IDs + demo knobs
  events.py            EventBus (SSE event shape) + agent-name constants
  orchestrator.py      run_code_red(): fan out agents, coordinate, close stream
  agents/
    action.py          Computer Use loop -> nearest police station (Maps)
    comms.py           Twilio SMS + voice, simulated fallback
    verification.py    Gemini Flash threat classification, deterministic fallback
kavach/
  server.py            FastAPI: /trigger + /session/{id}/events, serves web/
  web/                 the SSE-consuming demo UI
```

## Setup

```bash
cd /path/to/KAVACH-DEEPMIND-v2
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
playwright install chromium          # required for the live Computer Use browser
```

Create `.env` at the repo root:

```
GEMINI_AI_KEY=<your key>             # note: _AI_, not _API_
# optional — Comms goes live if all four are set (else it simulates):
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_FROM_NUMBER=+1...
EMERGENCY_CONTACTS=+9198...,+9199... # Twilio trial: verified numbers only
# optional demo knobs:
KAVACH_HEADLESS=false                # true = hide the Computer Use browser
KAVACH_DEFAULT_LAT=12.9716
KAVACH_DEFAULT_LNG=77.5946
```

## Run

```bash
cd kavach
python server.py            # http://localhost:8000
```

Open http://localhost:8000, hit the Code Red button (or triple-tap). The frontend
POSTs `/trigger`, then opens `EventSource('/session/1/events')` and renders the
live agent stream.

## Contract (do not change — frontend already speaks it)

- `POST /trigger` — `{action, mode, source}`, `action ∈ {code_red, mode_switch, resolve}`
  → `{status, mode}`.
- `GET /session/{id}/events` — SSE, each event:
  ```json
  {"stage":"Action","agent":"Computer Use","message":"...","status":"active","mode":"online"}
  ```
- `agent` ∈ `Antigravity` (orchestrator), `Computer Use` (action),
  `Live Voice` (comms), `Omni` (verification). `status == "failed"` renders red.

## Fallback behavior

- No Chromium / Computer Use error → real, openable Maps search link.
- No Twilio creds or contacts → simulated SMS + call events.
- No/failed Gemini call in Verification → deterministic HIGH-threat assessment.
- Backend down entirely → the web app runs its own local mock sequence.
