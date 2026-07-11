# Kavach — Ambient Sentinel + Live Voice Companion (Design)

Date: 2026-07-11
Status: Approved architecture; pending spec review.

## 1. Goal

Add a hands-free layer to the Kavach online branch:

1. **Sentinel** — laptop mic + `gemini-3.5-flash` continuously listen to the
   environment, transcribe, and classify danger/distress, streaming live
   captions + threat level to the web dashboard.
2. **Auto Code Red** — on distress (safeword or semantic distress) the sentinel
   auto-fires the *existing* orchestrator (Action / Comms / Verification). No
   button press needed. Everything stays in sync on one SSE stream.
3. **Live Voice Companion** — if an earphone is connected, spin up a real-time
   two-way voice agent (Gemini Live API, native audio) that privately coaches
   the victim ("help is 4 minutes away, walk toward the lit street on your
   right") until help arrives.

Runs backend-side on the demo laptop now; Android is a later phase.

## 2. Decisions locked (from user)

- **Audio location:** Backend (laptop) mic/speakers + web dashboard (visual
  only). Android integration is the final phase.
- **Trigger model:** Sentinel-as-trigger — monitoring detects distress and
  auto-invokes the existing `run_code_red`. All components share one EventBus.
- **Voice role:** Calm victim companion/coach, private (earphone).
- **Live model:** `gemini-3.1-flash-live-preview` (native audio dialog).
- **Sentinel model:** `gemini-3.5-flash` (rolling audio-chunk transcribe +
  classify).
- **Reference implementation:** `command-line/python` from
  https://github.com/google-gemini/gemini-live-api-examples — 4 async-task
  pattern (listen → send → receive → play), **PyAudio**, input 16kHz/mono/
  16-bit/1024-chunk, output 24kHz.
- **No frontend contract change:** reuse SSE `/session/{id}/events` + existing
  agent-name→icon map. Sentinel captions → `Omni`; voice agent → `Live Voice`.

## 3. Grounding: Gemini Live API facts (from official docs)

- Transport: stateful WebSocket via `client.aio.live.connect(model, config)`.
- Input audio: raw PCM 16-bit **16 kHz** LE → `session.send_realtime_input(
  audio=types.Blob(data=..., mime_type="audio/pcm;rate=16000"))`.
- Output audio: raw PCM 16-bit **24 kHz** LE, read from `session.receive()`
  (`server_content.model_turn.parts[*].inline_data.data`).
- Config fields: `response_modalities=["AUDIO"]`, `system_instruction`,
  `speech_config.voice_config.prebuilt_voice_config.voice_name`,
  `input_audio_transcription`, `output_audio_transcription`.
- Automatic VAD + barge-in on by default. Audio-only session limit: 15 min.

## 4. Architecture

```
Laptop mic ─►[Sentinel]  gemini-3.5-flash: chunk → transcribe + distress score
                 │  emit captions/threat ──► EventBus ──► SSE ──► web dashboard
                 ▼  safeword match OR distress ≥ threshold
            [Auto Code Red] ──► run_code_red()  (existing orchestrator)
                 │                    Action / Comms / Verification
                 ▼  earphone connected?
            [Voice Companion]  Gemini Live (gemini-3.1-flash-live-preview)
                 mic ◄─► earphone, native audio, barge-in
                 └ input/output transcription ──► "Live Voice" captions ──► SSE
```

Single `EventBus` per session → one synchronized SSE stream drives the whole UI.

## 5. Components (backend)

### 5.1 `backend/audio.py` — audio I/O + device detection
- `AudioIn` — PyAudio input stream (16kHz, mono, paInt16, 1024). Async
  generator yielding PCM chunks.
- `AudioOut` — PyAudio output stream (24kHz, mono, paInt16). `play(chunk)`.
- `earphone_connected() -> bool` — macOS via `system_profiler SPAudioDataType`
  (look for headphone/AirPods/USB/BT output as the default). Override:
  `KAVACH_FORCE_EARPHONE` env (`true`/`false`) for deterministic demo control.
- Graceful import guard: if PyAudio missing, expose `AVAILABLE = False`.

### 5.2 `backend/agents/sentinel.py` — ambient distress listener
- `run_sentinel(bus, stop_event) -> "distress" signal`.
- Rolling window: collect ~4s of mic PCM → send to `gemini-3.5-flash`
  (`generate_content` with an audio `Part` + classify prompt) → returns
  `{transcript, distress: bool, level: LOW|MED|HIGH, reason}`.
- Emit each result as an `Omni` event (stage `"Monitoring"`): live transcript +
  threat read on screen.
- Trigger conditions (either): safeword substring (`config.SAFEWORD`, instant,
  no model needed) OR model `distress==True` with `level>=HIGH`.
- Returns/sets a signal the monitor loop awaits; keeps listening otherwise.
- Fallback: no mic / PyAudio missing / model error → after N seconds emit a
  simulated distress event so the demo still advances (guarded by env flag
  `KAVACH_SIMULATE_DISTRESS`).

### 5.3 `backend/agents/voice.py` — live voice companion
- `run_voice_companion(bus, incident_context)`.
- Based on the official `command-line/python` example: 4 async tasks over
  `client.aio.live.connect(model="gemini-3.1-flash-live-preview", config=...)`:
  - `listen()` — mic PCM → out queue.
  - `send()` — queue → `session.send_realtime_input(...)`.
  - `receive()` — `session.receive()` → play queue + push
    input/output transcripts as `Live Voice` captions to the bus.
  - `play()` — queue → speaker (24kHz).
- `system_instruction`: calm, concise victim-coach persona; references incident
  context (location, ETA, safe zone from the orchestrator's Action result).
- Voice: `prebuilt_voice_config.voice_name` (e.g. "Kore" — calm).
- Bounded by an incident stop event and the 15-min session cap.
- Fallback: Live model unavailable / connect fails → emit a single scripted
  `Live Voice` reassurance line (no audio) so the UI still shows the companion.

### 5.4 `backend/monitor.py` — sentinel lifecycle + orchestration glue
- `start_monitoring(bus)`: launch sentinel; on distress signal → emit an
  `Antigravity` "distress confirmed, escalating" event → `await run_code_red(
  bus, lat, lng)` (existing) → if `earphone_connected()` → `run_voice_companion(
  bus, ctx)`. Then `bus.close()`.
- `stop_monitoring()`: set stop event, cancel tasks.
- Note: `run_code_red` currently closes the bus in its `finally`. Refactor to a
  `close` flag (default True) so the monitor can keep the stream open for the
  voice companion afterward. Single, targeted change to `orchestrator.py`.

### 5.5 Live location tracking (self-hosted)

Desktop Google Maps **cannot** start a real-time share (Google docs: "You must
use a mobile device"). So we host our own live link the demo fully controls;
native Google Maps live share is deferred to the Android phase.

- On Code Red, generate a short `token`; `live_url = <base>/live/{token}`.
- **Device → backend:** the web dashboard reads `navigator.geolocation`
  (watchPosition) and POSTs `{lat,lng}` to `/live/{token}/update` every few
  seconds. (Laptop demo: browser geolocation; if permission denied, use the
  existing `DEFAULT_LAT/LNG` as a static position.)
- **Contacts → tracker page:** `GET /live/{token}` serves a standalone page
  (`kavach/web/live.html`) rendering a **Leaflet + OpenStreetMap** map (no API
  key) that polls `GET /live/{token}/pos` and moves the marker live.
- **Comms** SMS body includes `live_url` (the live tracker) alongside the
  Action agent's police-station route link. The two are distinct: live_url =
  "where the victim is now"; route link = "nearest safe zone".
- In-memory store `{token: {lat,lng,ts}}`; token expires with the session.
- Fallback: no geolocation → tracker shows the static `DEFAULT_LAT/LNG`.

## 6. Server / API changes (`kavach/server.py`)

- `POST /trigger` gains `action ∈ {start_monitor, stop_monitor}`:
  - `start_monitor` → create session bus + launch `monitor.start_monitoring`.
  - `stop_monitor` / `resolve` → `monitor.stop_monitoring` + end session.
- Monitoring reuses the same session id ("1") and `/session/1/events` SSE.
- Existing `code_red` button path unchanged (still works as manual trigger).
- Live-location endpoints: `GET /live/{token}` (tracker page),
  `POST /live/{token}/update` (device pushes `{lat,lng}`),
  `GET /live/{token}/pos` (latest position JSON for polling).

## 7. Frontend changes (`kavach/web`, additive only)

- Hub gains a **"Start Monitoring"** toggle/button (distinct from Code Red).
- New monitoring panel: live transcript line + threat chip, fed by `Omni`
  events with stage `"Monitoring"`.
- On auto-trigger, reuse the existing active view / event log (already renders
  all agent events). Voice-companion captions render via the existing
  `Live Voice` mapping (green phone icon).
- On Code Red, dashboard starts `navigator.geolocation.watchPosition` and
  POSTs to `/live/{token}/update`. New standalone `live.html` tracker page
  (Leaflet + OSM) for contacts.
- No change to the SSE parsing contract or agent-name map.

## 8. Error handling / fallback philosophy (demo never hard-fails)

| Failure | Degradation |
|---|---|
| PyAudio / portaudio missing | `audio.AVAILABLE=False`; sentinel simulates distress after delay |
| No mic access | same simulated path |
| Flash classify error | safeword substring still triggers; else keep listening |
| Earphone detect fails | `KAVACH_FORCE_EARPHONE` env decides |
| Live model/connect fails | scripted `Live Voice` text line, no audio |
| Backend down entirely | web app's existing local mock |

## 9. New dependencies

- `pyaudio` (Python). System: `brew install portaudio` (PyAudio build dep).
- Add to `requirements.txt`. Live model must be confirmed callable on the key
  at build start (bidi connect smoke test).

## 10. Configuration additions (`backend/config.py`)

- `MODEL_SENTINEL = gemini-3.5-flash`
- `MODEL_LIVE_VOICE = gemini-3.1-flash-live-preview`
- `LIVE_VOICE_NAME = "Kore"`
- `KAVACH_FORCE_EARPHONE` (optional bool override)
- `KAVACH_SIMULATE_DISTRESS` (optional bool, demo fallback)
- `SENTINEL_CHUNK_SECONDS = 4`, `SENTINEL_LEVEL_TRIGGER = "HIGH"`

## 11. Testing strategy

1. `audio.py` standalone: record 2s, play back; print `earphone_connected()`.
2. Live bidi smoke test: connect `gemini-3.1-flash-live-preview`, send a short
   PCM buffer, confirm audio + transcript frames received.
3. Sentinel unit: feed a fixture WAV with the safeword → expect distress signal;
   feed neutral audio → no trigger.
4. Monitor integration (headless-safe): stub sentinel to emit distress →
   confirm `run_code_red` runs and (with `KAVACH_FORCE_EARPHONE=true`) voice
   companion path is entered; assert event sequence over the bus.
5. Live-location: `/live/{token}` endpoints + `live.html` tracker → open link
   in a second browser, confirm marker moves as the dashboard posts geolocation.
6. End-to-end manual: `start_monitor` → speak safeword → watch dashboard
   escalate → hear companion in earphone → contact opens live link, sees
   position update.

## 12. Phasing (implementation order)

1. `config.py` additions + `audio.py` + earphone detect (standalone test).
2. Live bidi smoke test against the key.
3. `sentinel.py` + `monitor.py` + `orchestrator.py` `close`-flag refactor +
   `/trigger` actions; auto-trigger works with simulated/real distress.
4. `voice.py` live companion, gated on earphone.
5. Live-location: `/live/{token}` endpoints, in-memory store, `live.html`
   tracker (Leaflet+OSM); Comms SMS includes `live_url`.
6. Frontend: Start Monitoring control + monitoring panel + geolocation posting.
7. Android integration (final phase, separate spec) — native Google Maps live
   share replaces the self-hosted link on device.

## 13. Out of scope

- Video input to Live API. Offline (Gemma) branch. Twilio changes.
- Android wiring (deferred to its own spec in the final phase).
