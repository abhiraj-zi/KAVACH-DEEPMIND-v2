# Kavach — Team Workflow (4 people, 10:30 → 17:00)

**Deliverable:** a demoable **web app** (run on localhost, opened on a phone) — a zero-UI safety system with two branches: **Ghost Operator** (online, Antigravity orchestrates parallel Gemini agents) and **Dark Survival** (offline, Gemma 4 on the edge).

**Primary track:** PS2 Autonomous Orchestration (iAPI / Antigravity) · Grand contention.
**Also contends:** Gemma 4 Local-First ($2000) via Dark Survival · **PS1 flavor** via the live voice agent.
**One sentence (memorise):** *"One trigger, four AI agents get her to safety — and when the signal dies, the last line of defense keeps working on the phone itself."*
**Golden rule:** ONE miracle, not five. The spine below IS the score.

Design detail: [`superpowers/specs/2026-07-11-kavach-two-branch-design.md`](superpowers/specs/2026-07-11-kavach-two-branch-design.md). Pitch: [`kavach-pitch-3min.md`](kavach-pitch-3min.md).

---

## 0. Architecture at a glance

```
CODE RED  (button = reliable demo · safeword "battery at 2 percent" = story)
     │
     ▼   signal?
 ┌───────────────── ONLINE ─────────────────┐   ┌──────────── OFFLINE / JAMMED ───────────┐
 │ GHOST OPERATOR                            │   │ DARK SURVIVAL (Gemma 4 E2B, on-device)  │
 │ Orchestrator (Antigravity/iAPI)           │   │ Edge Sentinel (native audio, safeword)  │
 │  ├─ Action Agent (3.5 Flash + Computer    │   │ Ghost Camouflage (sim now / Android      │
 │  │   Use, Playwright): Maps/Ola → safe zone│   │  roadmap):                               │
 │  ├─ Comms Agent (3.1 Flash Live): live    │   │  ├─ Dead-Screen Illusion (web blackout) │
 │  │   voice call to contact + patch-in      │   │  ├─ SOS Beacon (queue GPS, fire on bar) │
 │  └─ Verification (3.5 Flash): audio CONFIRM│   │  └─ Acoustic Siren (real loud tone)     │
 └───────────────────────────────────────────┘   └──────────────────────────────────────────┘
                    └────────── MODE HANDOFF: toggle airplane → falls back live ──────────┘
```

### The spine (must work)
```
trigger → Orchestrator → Action Agent drives browser to safety + Comms calls contact
       → toggle offline → Dark Survival (dead-screen + siren + SOS queue)
```

### Ban-safety (keep visible)
Live safety session, **not a dashboard**. Not Streamlit. Siren, not a harm-engineered tone. Hardware defenses labeled **"simulated today / native Android roadmap."** Prior medical work declared unprompted.

---

## 1. Roles & ownership (one owner per surface)

| Person | Role | Owns | Deliverable |
|---|---|---|---|
| Backend eng | **ORCHESTRATOR + COMMS + BACKEND + INTEGRATION** | `host.py`, `tools.py`, `guardian_system_instruction.txt`, `voice.py`, `server.py` | Antigravity orchestration + re-plan, Live voice call, FastAPI + SSE, mode-switch, integration hub |
| ML / prompt eng | **DARK SURVIVAL (Gemma) + VERIFICATION** | `sentinel.py` | Offline Gemma sentinel + survival logic + audio confirmation |
| Android eng | **ACTION AGENT + WEB FRONTEND** | `operator_agent.py`, `web/` | Computer-Use browser (Maps/Ola) + the demoable UI + sim (dead-screen/siren/SOS-queue) |
| Physician | **STORY / TRUST / DEMO / UX COPY** | `kavach-pitch-3min.md`, submission | 3-min pitch + 1-min video + submission; plays the user |

**Contingency:** if Android eng can't do frontend, Backend builds the React shell; Android does the Action Agent only.

---

## 2. RACI (key artifacts)

- **Orchestrator + re-plan (Antigravity)** — R/A: Backend · C: ML
- **Action Agent (Computer Use / Playwright)** — R/A: Android · C: Backend
- **Comms Agent (3.1 Flash Live)** — R/A: Backend · C: ML
- **Dark Survival (Gemma) + Verification** — R/A: ML · C: Backend
- **Web frontend + simulations** — R/A: Android · C: Physician (copy)
- **Mode handoff (online↔offline)** — R: Backend + ML (pair) · A: Backend
- **12:30 Computer-Use-vs-scripted call** — R: Android · A: whole team
- **Recorded run (15:30)** — R/A: Android · C: all
- **Pitch + video + submission** — R/A: Physician · C: all

---

## 3. Tech stack + frozen contracts

- **Frontend:** React (Vite) or vanilla JS. Two live views (Ghost Operator / Dark Survival) + trigger button + simulations.
- **Backend:** FastAPI `server.py` → `POST /trigger`, `GET /session/{id}/events` (SSE). Runs Orchestrator + Comms + mode-switch.
- **Action Agent:** Gemini 3.5 Flash Computer-Use over Playwright Chromium. Fallback: scripted browser nav (Maps URL + pre-filled SOS).
- **Gemma 4:** E2B on-device via Ollama / AI Edge Python, offline. **Voice:** Gemini 3.1 Flash Live.
- **Freeze at 10:30 (so all 4 parallelize):**
  - `CodeRed` = `{source, timestamp, location}`
  - Orchestrator↔agent = `dispatch(task) -> {status, detail}`
  - SSE event = `{stage, agent, model, message, status, mode}`
  - connectivity = `mode: "online"|"offline"`
- **Env:** `pip install -r requirements.txt`; `export GOOGLE_API_KEY=...` (confirm at kickoff). Run backend from repo root; frontend dev server separately; on the phone open the laptop LAN IP.

---

## 4. Config at kickoff (first 5 min)

- `tools.py :: EMERGENCY_CONTACTS` — teammate's real number (Comms call + SOS land live).
- `tools.py :: DEMO_LOCATION` — venue coordinates.
- `operator_agent.py :: USE_COMPUTER_USE` — `True`; flip `False` at 12:30 if fragile.
- `sentinel.py` — confirm Gemma 4 E2B runs offline on the laptop.
- Confirm the 4 model IDs; don't hardcode blind.

---

## 5. Hour-by-hour (parallel lanes)

**09:00–10:30 Kickoff:** confirm accounts + 4 model IDs; freeze the 4 contracts; Physician fills config + drafts pitch.

**10:30–11:30 SPIKE:**
- ML: Gemma offline loop (safeword → outcome) on the laptop.
- Android: web app shell + button + live-events panel on **fake** events; one Computer-Use call opens Maps in a browser.
- Backend: bind Antigravity `start_guardian` + `send_utterance`; reach ACTIVE with mocked tools.
- Physician: pitch + Q&A.
- **Gate:** button fires a mock chain end-to-end on screen; Gemma detects the safeword offline.

**11:30–12:30 Bind seams:**
- Backend: tool-call extract/submit; FastAPI `/trigger` + SSE; Comms Live voice stub → real; mode-switch.
- ML: Dark Survival logic + Verification audio.
- Android: `_computer_use_step` browser Action Agent; UI on real events.

**🔴 12:30 CHECKPOINT (2-min huddle):** Computer Use reliable in-browser? keep it : deterministic browser nav. Final.

**12:30–13:00 Lunch (shifts).**

**13:00–14:00 Real online chain:** trigger → Orchestrator → Action Agent (Maps/Ola) + Comms call to contact, live.

**14:00–14:45 The two signature beats:**
- Backend: Action-fail → re-plan (notify direct), shown as a toggle.
- ML + Android: the **mode handoff** — toggle offline → Dark Survival: dead-screen + siren + SOS-queue. The money shot.

**14:45–15:30 P1 polish:** UI on both live views; live-location; report card. Cut ruthlessly if spine fragile.

**🎥 15:30–16:15 RECORD** the flawless online→offline run (protect above all) + integration rehearsal.

**16:15–16:45** timed 3:00 rehearsal (Physician), twice.

**16:45–17:00 Submit:** public repo · 1-min video (today only, recorded run) · form · all 4 members.

**Slip → cut a feature, never the recording or the rehearsal.**

---

## 6. Definition of done (per checkpoint)

- **11:30:** button fires full mock chain on screen; Gemma offline safeword works.
- **12:30:** Computer-Use decision made; state path green on mocks; contracts wired.
- **14:00:** live trigger → Action Agent browser navigation + Comms call.
- **15:00:** re-plan + mode handoff (online→Dark Survival) + all 3 offline sims.
- **15:30:** flawless run recorded, saved off-device.
- **17:00:** submitted, repo public, all members added.

---

## 7. Demo runbook (3:00 — see the pitch doc for the script)

1. Pitch cold open (woman-doctor story) → show the web app on the phone.
2. Trigger (button/safeword) → **Ghost Operator**: Action Agent drives the browser to safety + Comms calls the teammate's phone (it rings in the room).
3. **Mode handoff:** toggle airplane → **Dark Survival**: dead-screen blackout + siren + SOS-queue.
4. De-escalation replay ("I'm fine" → stands down).
5. Close on the one sentence.

**Presenter rule:** recorded run is the safety net; if anything wobbles, cut to it and keep narrating.

---

## 8. Fallback ladder (rehearse top three)

- Computer Use misclicks → deterministic browser nav → recorded run.
- Antigravity flaky → local state machine in `host.py`, keep the multi-agent framing.
- Live voice flaky → on-screen call transcript.
- Gemma slow → keyword safeword match.
- Wifi dies → that IS the Dark Survival demo.

---

## 9. Submission checklist (17:00)

- [ ] Repo **public**.
- [ ] 1-min video — today's work only; prior work declared unprompted; hardware sims labeled.
- [ ] Recorded online→offline run embedded.
- [ ] All 4 members on the form; demo link checked from another device.

---

**Bottom line:** one trigger, two branches, four models, one web app. Ghost Operator is the autonomous miracle; Dark Survival is the offline differentiator and the Gemma prize. Freeze the contracts, mock the seams, build the spine first, and label the simulated hardware honestly.
