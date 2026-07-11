# Kavach — Two-Branch Design Spec (Ghost Operator / Dark Survival)

**Event:** Google DeepMind x Cerebral Valley Bangalore Hackathon - 11 Jul 2026
**Deliverable:** a demoable web app (run on localhost, opened on a phone browser)
**Primary track:** PS2 Autonomous Orchestration (iAPI / Antigravity) - Grand-prize contention
**Also contends:** Gemma 4 Local-First special prize ($2000); demonstrates PS1 real-time voice
**One sentence:** *"One trigger - and four AI agents get her to safety. When the signal dies, the last line of defense keeps working on the phone itself."*

---

## 1. Purpose & framing

A zero-UI personal-safety system for women. The user's job ends at a single discreet trigger; from there the system autonomously navigates her to safety, calls for help, and - if the network drops or is jammed - falls back to an on-device survival mode. The demo is a web app so it is reliable and self-contained; the deepest hardware defenses are **simulated in the web app and declared as the native Android roadmap** (honesty is a hackathon rule; overclaiming risks disqualification).

## 2. Trigger (Code Red)

- **Primary (demo):** a discreet in-app **button** - reliable on stage.
- **Story (secondary):** a spoken **safeword** ("Hey, my battery is at 2 percent") via the on-device Gemma sentinel.
- Both emit the same `CodeRed` event. The system then branches by connectivity.
- **No blind distress inference.** Ambient audio is used only to *confirm/upgrade* an already-triggered Code Red, never to trigger it. (Defends the "how do you tell distress from a loud street?" question.)

## 3. Branch 1 - Ghost Operator (online)

Fires when data/Wi-Fi is available. The Antigravity manager orchestrates parallel Gemini agents.

- **Orchestrator Agent (Antigravity / Interactions API):** ingests the Code Red, classifies severity, spawns the sub-agents in parallel, holds server-side state, and **re-plans** if a sub-agent fails (the PS2 differentiator). No screen needed.
- **Action Agent (Gemini 3.5 Flash + Computer Use, via Playwright):** zero-UI browser manipulation. Reads GPS, opens Maps / Ola / Uber, routes to the nearest safe zone or police station. **Demo boundary:** stops at the booking-confirmation screen - no real payment/auth - and this is stated aloud.
- **Comms Agent (Gemini 3.1 Flash Live):** live, bi-directional synthesized voice call to an emergency contact: *"This is Kavach. [Name] triggered a silent alarm at [location]. A cab has been dispatched. I'll patch you into their live microphone."* Demo uses a teammate's phone in the room; real PSTN telephony is roadmap.
- **Verification Agent (Gemini 3.5 Flash):** processes ambient audio context to **upgrade** the threat level and stream context to the Orchestrator. Confirmation only - never the trigger.

## 4. Branch 2 - Dark Survival (offline)

Fires when data drops or the signal is jammed. Everything shifts on-device to Gemma 4 (E2B, mobile-quantized, native raw-audio - no cloud STT).

- **Edge Sentinel (Gemma 4 audio):** continuous offline listening; detects the offline safeword with near-zero latency; can flag acoustic escalation as *secondary* context (not a trigger).
- **Ghost Camouflage** - simulated in the web app now, native Android on the roadmap:
  - **Dead-Screen Illusion:** the web page slams brightness/UI to black so a grabbed phone looks dead. (Native power-button/UI lock needs device-admin/root - roadmap.)
  - **SOS Beacon (queued):** Gemma composes a GPS SOS offline and shows a "queued" log; it fires SMS the instant any cell bar returns. (A web app cannot send SMS silently; native Android with SEND_SMS - roadmap. SMS needs cell, not data.)
  - **Acoustic Siren:** a real, loud, attention-drawing browser tone on a violent-grab or manual trigger. (Reframed from the "120 dB weaponized tone"; no OS volume-cap bypass, no tone engineered to harm.)

## 5. The mode handoff (the signature beat)

Connectivity is monitored continuously. Mid-demo, airplane mode is toggled; the app visibly falls back Ghost Operator -> Dark Survival and keeps protecting the user. This single transition is the thing no other safety app can show and is the emotional peak of the pitch.

## 6. Architecture (components & boundaries)

- **Frontend (`web/`):** React (Vite) or vanilla JS. Not Streamlit. Two live-session views (Ghost Operator / Dark Survival), the trigger button, and the simulated dead-screen/siren/SOS-queue. Framed as a live safety session, NOT a dashboard (banned).
- **Backend (`server.py`, FastAPI):** exposes `POST /trigger`, `GET /session/{id}/events` (SSE). Runs the Orchestrator loop, the Comms agent, and the mode-switch; streams events to the UI.
- **Orchestrator (`host.py` + Antigravity):** state machine + parallel delegation + re-plan.
- **Tools (`tools.py`):** the delegation contracts the Orchestrator calls.
- **Action Agent (`operator_agent.py`):** Computer Use over a Playwright-controlled browser; deterministic fallback = scripted browser navigation (open Maps URL + pre-filled SOS).
- **Edge Sentinel + Dark Survival (`sentinel.py`):** Gemma 4 offline loop + the offline survival logic.
- **Comms voice (`voice.py`):** Gemini 3.1 Flash Live session.

### Frozen contracts (enable parallel work from 10:30)
- `CodeRed` event: `{source: "button"|"safeword", timestamp, location}`.
- Orchestrator <-> sub-agent: `dispatch(task) -> {status: "done"|"failed", detail}`.
- Backend -> frontend SSE event: `{stage, agent, model, message, status, mode}`.
- Connectivity: `mode: "online"|"offline"`.

## 7. Error handling / fallback ladder

- Computer Use misclicks -> deterministic browser navigation; else the recorded run.
- Antigravity flaky -> run the state machine locally in `host.py`, keep the multi-agent framing.
- Live voice flaky -> on-screen text call transcript.
- Gemma slow -> keyword safeword match; Gemma still does phrasing.
- Wifi dies on stage -> that IS the Dark Survival demo; zero dependency.
- Any total failure -> the pre-recorded flawless run, narrated live.

## 8. Test plan (smoke per layer)

1. Button -> `CodeRed` -> SSE event visible in UI (mock agents).
2. Action Agent opens Maps in the browser to a destination.
3. Comms Agent places a live voice call to the teammate phone.
4. Mode toggle -> UI switches Ghost -> Dark Survival.
5. Dark Survival: dead-screen blackout + siren + SOS-queue log.
6. Full run online->offline, recorded.

## 9. Prize mapping

- **PS2 (primary):** parallel multi-agent orchestration + re-plan (Antigravity + Computer Use + Live).
- **Gemma Local-First ($2000):** offline Edge Sentinel + Dark Survival = real on-device sense-decide-act-check.
- **PS1 flavor:** live voice Comms agent.
- **Creativity 35 / Impact-India 25:** woman-doctor story + the online->offline handoff.

## 10. Ban compliance (shown, not just claimed)

- Not a dashboard - a live safety session surface.
- Not Streamlit - React/vanilla + FastAPI.
- No weaponized/harmful output - siren reframed; no engineered-harm tone.
- No overclaiming - hardware defenses labeled "simulated today / native Android roadmap."
- New work only - prior medical work declared unprompted, not part of this build.

## 11. Open items to confirm at kickoff

- Exact model IDs: Antigravity `antigravity-preview-05-2026`, `gemini-3.5-flash` (Computer Use), `gemini-3.1-flash-live-preview`, Gemma 4 E2B on-device.
- Whether the Comms agent can place a real call via a provisioned path, or stays web-audio for the demo.
- Playwright vs the Computer-Use browser environment for the Action Agent.
