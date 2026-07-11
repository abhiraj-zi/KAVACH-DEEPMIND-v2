# Kavach — Judge Q&A Battle Card

**Judging weight:** Creativity 35 · Live Demo 25 · Impact-India 25 · Tech Depth 15.
**Golden rules of Q&A:** (1) Answer in 1-2 sentences, then stop. (2) When you can *show* instead of tell, show. (3) Never overclaim — "simulated today, Android roadmap" is a strength, not a weakness. (4) If you don't know, say "great question, here's our current thinking" — never bluff.

---

## The 5 hardest — memorise these one-breath answers

1. **"Isn't this just a panic button?"**
   > "A panic button needs her to keep acting — hold it, wait, hope someone responds. Kavach needs one trigger, then four agents act *for* her, re-plan when a step fails, and keep working offline. The button is the *only* thing she does."

2. **"What did you actually build today vs. simulate?"**
   > "Real today: the multi-agent orchestration, the browser Action Agent, the live voice call, and the on-device Gemma offline loop. Simulated today: the phone-hardware defenses (dead-screen, SMS beacon) — those need native Android, which is our roadmap on Google's own AICore. We label that clearly; we're not claiming hardware we didn't build."

3. **"How do you tell distress from a normal loud street?"**
   > "We don't — and that's deliberate. We never infer distress from ambient audio; that's scientifically thin. The only trigger is an explicit, deniable safeword or button. Audio is used *only* to raise confidence *after* she's already triggered, never to start it."

4. **"What about false alarms? A false dispatch is a real cost."**
   > "Nothing costly is irreversible. On trigger we start only silent, reversible actions; every external action is gated behind a ~10s window she can cancel with a secret PIN. Police are never auto-dialed — that always defers to a human."

5. **"Why would Google adopt this?"**
   > "It's not a new app — it's the agentic layer that turns Android's existing Personal Safety from *notify* into *act*, on the on-device Gemma stack Google already ships in AICore. Offline-first, private, distributable to a billion phones on day one."

---

## Creativity / originality (35%) — "I've seen safety apps"

**"Safety apps already exist — bSafe, Himmat, 112, Safetipin. What's new?"**
> "Every one of those is reactive and network-dependent — they notify, then wait on infrastructure, and they die exactly where she's most alone: offline. Our two things nobody has: agents that *act* autonomously, and a full offline survival mode on-device. Research on Indian safety apps names 'heavy dependence on internet and GPS' as the core failure — that's the gap we close."

**"Android already has Emergency SOS and Safety Check. Why you?"**
> "Those *share your location and wait*. Kavach *acts* — routes you, books transport, patches a human in — and keeps going with no signal. We're the agentic upgrade to that exact feature, not a competitor to it."

**"Isn't the 'AI agent' just a wrapper that opens Maps and sends an SMS?"**
> "If it were one script, yes. It's an orchestrator delegating to specialised agents in parallel, reading results, and *re-planning* when one fails — I can show it fail the cab step and recover by alerting a human directly. That feedback loop is the difference between a macro and an agent."

---

## Technical depth (15%) — "prove it's real"

**"How is this multi-agent and not one prompt with function-calling?"**
> "The Orchestrator (Antigravity) holds state and delegates; the Action Agent (Computer-Use) and Comms Agent (Live) run independently and report back; the Orchestrator re-plans on failure. Separate models, separate jobs, a shared state and a real handoff — I'll show the trace."

**"Why four models? Isn't that over-engineering?"**
> "Each does what it's uniquely best at: Gemma for private on-device + offline, 3.1 Live for real-time voice, Antigravity for multi-step orchestration, 3.5 Computer-Use for acting on a screen. One model can't be offline-on-device *and* orchestrate cloud actions — the split is the architecture, not decoration."

**"Computer Use is preview and misclicks. Is the demo scripted?"**
> "Computer-Use does the reasoning live over the real page. We keep a deterministic navigation fallback for reliability, and a recorded run as insurance — that's engineering discipline for a preview API, and we'll tell you which mode you're watching."

**"A web app can't disable the power button or send silent SMS. So that's fake?"**
> "Correct — and we don't claim otherwise. In the web app those are honest *simulations*; the production versions need native Android permissions (device-admin, SEND_SMS), which is our roadmap on AICore/Gemma. We drew the line at what's real deliberately, because overclaiming is disqualifying."

**"What exactly runs on-device vs. cloud?"**
> "Offline branch: Gemma 4 E2B (~sub-1GB, quantized) does safeword detection and the survival logic entirely on the device — airplane mode proves it. Online branch: the orchestration and Computer-Use are cloud. The whole point is it degrades gracefully from cloud to edge."

**"Show me it fail and recover."** *(WANT this — have it ready as a toggle)*
> *[Trigger the Action-Agent-fail path → Orchestrator re-plans → alerts a human contact directly.]* "That's the re-plan loop — no human in the loop, and help still goes out."

---

## False alerts & safety design

**"If an attacker grabs the phone, can't they cancel the alert?"**
> "No — cancelling requires a secret PIN or phrase only she set. Silence is treated as danger, so doing nothing *proceeds*. The attacker can't stop it; only she can."

**"Won't the siren endanger a woman who's hiding?"**
> "The siren is one branch, never the default. The default escalation is *silent* — dead-screen and a queued SOS. The loud response only fires on a violent-grab signal or her explicit choice."

**"Who decides to call the police? What if it's wrong?"**
> "The agent never auto-dials authorities — that's the highest-cost action, so it always defers to a human: the trusted contact who's been patched in, or her explicit confirm. That's the deliberate human boundary."

---

## Impact in India (25%) — "will it actually reach her?"

**"Rural women have cheap phones and no data. Does this really work for them?"**
> "That's the whole reason for the offline branch. Even with no data and a low-end phone, the on-device path fires an SOS the moment a bar returns; where AICore isn't available we degrade to a keyword rule engine. We built for the low-signal case first, not last."

**"Booking Ola needs an account and payment — unrealistic in a crisis?"**
> "The reliable core action is *navigation to the nearest safe place* plus alerting a human — that needs no payment. Cab-booking uses her pre-linked account and is optional; we never make her safety depend on a transaction."

**"Gemma 4 / AICore needs flagship hardware. That excludes most of India."**
> "E2B is mobile-quantized for mid-range NPUs (Tensor/MediaTek/Qualcomm), and where the model isn't supported we fall back to on-device keyword detection. Tiered by hardware, so no one is excluded from the *core* alert."

**"Isn't relying on contacts/police the same broken infrastructure that fails other apps?"**
> "We reduce that dependence: the phone *self-navigates* her to safety and captures evidence regardless of whether anyone responds. Human help is one layer, not the only layer — that's the fix, not the flaw."

**"Does it work in regional languages?"**
> "Yes — the safeword is user-chosen in any language, and the voice agent supports Indian languages; the on-device model keeps that private."

---

## Ethics / privacy / trust

**"Isn't always-on listening surveillance?"**
> "It's not always-on inference — it acts only on the button or the explicit safeword, and in offline mode the audio never leaves the device. She opts in to what listens and when."

**"Could an abuser misuse this to stalk or control the victim?"** *(the hardest ethics question)*
> "We took this seriously: contacts and settings are locked behind her PIN, there's no covert location-sharing *of* her *to* others without her setup, and data stays on-device. Coercive-control resistance is an explicit design constraint, not an afterthought."

**"If it fails and someone is harmed, who's liable?"**
> "It's an assistive layer that augments — never replaces — emergency services, it defers high-stakes actions to humans, and it keeps a consented local log. Same posture as Android's existing safety features."

**"You're a doctor — is any of this medical advice?"**
> "No. It never diagnoses or advises; it's a safety-orchestration tool. My clinical background informs the *trust and consent* design, nothing more — and my prior medical work is not part of this build; I'll say that unprompted."

---

## Business / adoption / scale

**"Who pays for the cloud agents at scale?"**
> "The expensive part — continuous monitoring — is on-device Gemma, which is free and local. Cloud agents fire only on a real Code Red, which is rare per user, so cloud cost per user is near-zero. That's the economic case for on-device-first."

**"Go-to-market — app, Google feature, or government?"**
> "Primary path: an agentic feature inside Android's Personal Safety, distributed via Play Services — near-zero adoption cost. Secondary: a standalone app and a B2G play with state women's-safety programs (112/NAPSE-style)."

**"If I gave you three months, what would you build?"** *(WANT this)*
> "The native Android layer — real dead-screen lock, real SMS beacon, real siren on AICore/Gemma — plus coercive-control hardening and a pilot with one city's women's-safety helpline."

---

## Self-inflicted traps to AVOID

- Do NOT say "it detects danger from her voice/tone" — you'll invite the unanswerable distress-inference question. It's a *safeword*.
- Do NOT imply the hardware defenses are built. Always "simulated today / Android roadmap."
- Do NOT claim it replaces police/112. It *augments* and *defers*.
- Do NOT oversell cab-booking as the core. Navigation + human alert is the reliable core.
- Do NOT hide prior work. Declare it unprompted in the first 20 seconds of Q&A if relevant.

## Questions you WANT (steer toward these)

- "Show me it fail and recover." → the re-plan toggle.
- "Prove the offline mode." → toggle airplane mode live.
- "What's the one thing that makes this win?" → "It works when the signal dies — the moment every other app fails."

---

**Drill before judging:** each teammate takes one category and the others fire the toughest question in it, twice. If any answer runs past two sentences, cut it. The physician leads; everyone can answer the top 5.
