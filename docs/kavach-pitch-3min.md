# Kavach — The 3-Minute Pitch (first person, for a woman doctor)

**Format:** you, holding your phone, running the web app live on localhost. No slides until the very end (optional). The story and the phone carry it.

**Emotional spine:** *You* are the protagonist. You're a doctor, you're a woman, you know the walk. The demo is not a feature tour — it's you showing the thing you wish you'd had.

**The one sentence (open and close with it):**
> "One trigger — and four AI agents get her to safety. And when the signal dies, the last line of defense keeps working on the phone itself."

Architecture behind the demo: [`superpowers/specs/2026-07-11-kavach-two-branch-design.md`](superpowers/specs/2026-07-11-kavach-two-branch-design.md).

---

## The arc (why it lands)

1. **Make them feel the fear** (they've all seen it in someone they love).
2. **Name why every existing app fails** (she has to do the work).
3. **One trigger — then the phone does something they've never seen** (Ghost Operator).
4. **The twist that seals it: the signal dies and it *keeps going*** (Dark Survival).
5. **Land on dignity, not fear** — a guardian, not a panic button.

---

## The script (≈3:00) — spoken lines + [stage directions]

### 0:00–0:22 · Cold open — you, the phone, no slides
> "I'm a doctor. Some nights I finish at 2 AM.
> And like every woman in this room who has walked to her car alone in the dark — I know that walk.
> Keys held between my fingers. A fake phone call to nobody. *'Haan Ma, I'm almost home.'*
> Every woman here has made that call."

*[Beat. Let it sit.]*

### 0:22–0:42 · The problem
> "In India, a woman's safety still depends on *her* doing the work — find the phone, unlock it, press the button, scream.
> In 2024 we were reminded, painfully, that not even a hospital is always safe.
> And every safety app fails at the exact moment it's needed: it assumes she has a free hand, a spare second, and a signal. She usually has none of the three."

### 0:42–0:58 · The turn (pick up the phone)
> "So we built Kavach. One trigger — and four AI agents do the rest.
> It's running on my phone right now, on localhost. No slides. Watch."

*[Hold the phone up so the room sees the screen.]*

### 0:58–1:10 · Trigger → Ghost Operator wakes
*[Tap the discreet button — or say the safeword: "Hey, my battery's at 2 percent."]*
> "I don't scream. I say one ordinary sentence. That's the trigger.
> An orchestrator agent hears it, calls a Code Red, and — without ever lighting up my screen — spawns a team of agents."

### 1:10–1:35 · Ghost Operator, Beat 1 — the Action Agent (Computer Use)
*[On screen: the browser drives itself — Maps/Ola opens, routes.]*
> "One agent takes the wheel. Using Gemini Computer-Use, it opens Maps, finds the nearest safe place, and books me a cab — I never touched the app.
> My hand is behind my back. It's doing this *for* me."

### 1:35–1:55 · Ghost Operator, Beat 2 — the Comms Agent (live voice)
*[A teammate's phone rings in the room.]*
> Kavach (calm synthesized voice): "This is Kavach. [Name] has triggered a silent alarm near [location]. A cab is on the way. I'm connecting you to their live audio now."
> "That's a real call, in a real voice, to the person I love — while I do nothing but keep walking."

### 1:55–2:25 · THE TWIST — the signal dies → Dark Survival
*[Toggle airplane mode. The UI visibly falls back.]*
> "Now the part no other app can do. Watch what happens when the network drops — or an attacker jams it."
*[Screen slams to black.]*
> "The phone plays dead — so if it's grabbed, it looks broken and gets ignored.
> On-device Gemma 4 keeps listening, queues an SOS with my GPS to fire the second a signal returns —"
*[Siren sounds.]*
> "— and if it's snatched, it screams. All of this, offline, on the phone. No cloud. No signal. Still protecting me."

### 2:25–2:42 · Credibility — a guardian, not a false alarm
*[Replay quickly: say "I'm fine."]*
> "And if I'm okay? It stands down. It's a guardian — not a panic button that cries wolf."

### 2:42–3:00 · The close
> "I'm a doctor. I spend my life responding to emergencies.
> Kavach makes a phone do the same — sense, decide, act, and call for help — for the one moment a woman can't do it herself.
> And when everything else fails, it fights on alone, in her pocket.
> One trigger. Four agents. And no one has to make that fake phone call again."

---

## Delivery notes

- **Slow down at the cold open and the close.** Silence after "made that call" and after "cries wolf" does more than any word.
- **The 2024 line is optional and yours to make.** In an Indian room it lands instantly without naming anyone; keep it dignified — the *reason this matters*, never a prop. If it feels heavy in rehearsal, cut it.
- **Say the honest line once** (pre-empts the ban/overclaim question): *"The offline defenses you're seeing are simulated in this web app today — the full hardware version is our Android roadmap."* Best placed right after the siren, in one breath.
- **Let the phone talk.** Narrate lightly; save the "four models / two branches" architecture for Q&A.
- **Hands-free is the point** — after the trigger, put your other hand behind your back so they *see* you never touch it again.

---

## Demo choreography (localhost on the phone)

- **Setup:** laptop runs the backend + Playwright browser; the phone opens the web app at the laptop's LAN IP (e.g. `http://192.168.x.x:5173`). Test the exact URL on the phone twice before you go up.
- **Contact phone:** a teammate holds the emergency-contact phone in the room so the Comms call + SOS land visibly.
- **The taps, in order:** (1) trigger button/safeword → (2) watch the Action Agent drive Maps/Ola → (3) the Comms call rings the teammate → (4) toggle airplane → Dark Survival: dead-screen + SOS-queue + siren → (5) replay, say "I'm fine," it stands down.
- **Safety net:** the pre-recorded flawless online→offline run is queued. If anything wobbles, cut to it and keep narrating in the same voice — the story never breaks.

---

## If you're running behind (2:00 cut)

Keep: cold open (0:00–0:22) → trigger + Action Agent (1:10–1:35) → the Dark Survival twist (1:55–2:25) → close (2:42–3:00).
Drop first: the Comms-call beat and the de-escalation replay. **Never drop the offline twist** — it's the differentiator and the Gemma prize.

---

## Evidence & Google adoption (appendix — for Q&A, the video description, and judges; not the spoken 3 min)

### The numbers that back the story
- **445,256 reported crimes against women in India in 2022 — ~51 FIRs every hour, and rising** (428,278 in 2021; 371,503 in 2020). National rate 66.4 per lakh; Delhi highest at 144.4. Source: [NCRB, Crime in India 2022](https://ncrb.gov.in/uploads/nationalcrimerecordsbureau/custom/1701935135TABLE3A1.pdf).
- **Reported is the tip of the iceberg:** NFHS-5 (2019–21) — **32% of ever-married women** have faced physical, sexual, or emotional violence; most never reaches the formal system. Source: [SPRF analysis](https://sprf.in/crimes-against-women-in-india-trends-challenges-and-policy-responses/).
- **The workplace/institution gap is live:** the 2024 RG Kar (Kolkata) trainee-doctor case reignited the question of women's safety at work and study — directly the world you speak from. Source: [IJIP 2025, Analysis of Women Safety Apps in India](https://ijip.in/wp-content/uploads/2025/09/18.01.262.20251303.pdf).

### Why every existing safety app fails (and Kavach's answer)
- **They're reactive and manual — she must act.** The panic button "assumes police will respond quickly every time" and shifts the burden onto the victim. Source: [Karusala & Kumar, CHI 2017](http://library.usc.edu.ph/ACM/CHI%202017/1proc/p3340.pdf). → **Kavach acts *for* her**: one trigger, agents do the rest.
- **They die where she's most alone.** Indian analyses find safety apps are "heavily dependent on stable internet and GPS, limiting effectiveness in rural or low-network areas," with crashes and battery drain; ~half of phones sold in India cost <$100 with substandard GPS/signal. Sources: [IJIP 2025](https://ijip.in/wp-content/uploads/2025/09/18.01.262.20251303.pdf), [CIS India](https://cis-india.org/internet-governance/blog/gender-it-rohini-lakshane-may-19-2016-womens-safety-there-is-an-app-for-that). → **Dark Survival runs offline on-device** — exactly where every other app goes dark.
- **A loud alarm can *hurt* a hiding victim; one size doesn't fit all.** Source: [CIS India](https://cis-india.org/internet-governance/blog/gender-it-rohini-lakshane-may-19-2016-womens-safety-there-is-an-app-for-that). → **Kavach's siren is one branch of many**; the default is silent, deniable escalation (dead-screen + queued SOS).
- **They reify "be vigilant at all times."** Source: [Safetipin, CHI 2023](https://doi.org/10.1145/3572334.3572392). → **Kavach removes vigilance from the victim** and puts it on the agent.

### The Google adoption path (why Google would ship this)
- **It's the agentic evolution of a product Google already ships.** The [Android Personal Safety app](https://play.google.com/store/apps/details?id=com.google.android.apps.safetyhub) already does Emergency SOS (power button x5), Emergency Sharing, and **Safety Check** (a check-in timer that auto-shares if you don't respond). Kavach upgrades "*share* my location" → "*autonomously get me to safety and call for help — even offline*." It's a feature slot, not a new category.
- **The offline substrate already exists: AICore + Gemma 4.** [Android AICore](https://support.google.com/android/answer/17065362) runs Gemini Nano / **Gemma 4 on-device, privately, and works in airplane mode** (Android 14+, Tensor/MediaTek/Qualcomm). Gemma 4 is available via the [AICore Developer Preview in 2026](https://stora.sh/blog/2026-04-13-on-device-ai-android-app-gemini-nano-guide), and code written against it is forward-compatible. **Dark Survival maps straight onto AICore/Gemma; Ghost Operator maps onto the Interactions API/Antigravity + Computer Use.**
- **Distribution and scale are already Google's.** ~1B Android users in India, Personal Safety preloaded/updatable via Play Services — the adoption cost of an on-device safety agent is near-zero for Google, and it lands hardest in exactly the low-signal regions the special prize is about.

### The one-line adoption pitch (for Q&A)
> "Kavach isn't a new app to install — it's the agentic layer that turns Android's existing Personal Safety from *notify* into *act*, using the on-device Gemma stack Google is already shipping in AICore. Offline-first, private, and distributable to a billion phones on day one."

## The three lines to survive Q&A on

- **Impact:** "This is for every woman who has made that fake phone call — and it works where she's most alone: offline."
- **Creativity:** "Two branches. Online, Antigravity orchestrates parallel agents that act for her. Offline, Gemma 4 fights on alone on the device — with a re-plan loop across both."
- **Honesty:** "The offline hardware defenses are simulated on web today; native Android is the roadmap. And my prior medical work is not in this build — I'll say so unprompted."
