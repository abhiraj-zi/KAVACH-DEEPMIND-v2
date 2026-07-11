# Kavach Ambient Sentinel + Live Voice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add hands-free ambient distress monitoring (Gemini Flash) that auto-fires the existing Code Red orchestrator, a real-time Gemini Live voice companion gated on earphone presence, and self-hosted live-location tracking — all streamed to the existing web dashboard over SSE.

**Architecture:** A backend sentinel listens to the laptop mic in rolling chunks and classifies distress with `gemini-3.5-flash`. On distress it invokes the existing `run_code_red` orchestrator, then (if an earphone is connected) opens a Gemini Live native-audio session (`gemini-3.1-flash-live-preview`) that privately coaches the victim. A self-hosted `/live/{token}` page (Leaflet + OpenStreetMap) shows the victim's browser geolocation updating in real time; Comms SMS carries that link. Everything shares one `EventBus` → one SSE stream.

**Tech Stack:** Python 3.11, google-genai (Live API), PyAudio (PortAudio), FastAPI/uvicorn, pytest + pytest-asyncio, Leaflet/OSM (frontend, CDN).

## Global Constraints

- Python interpreter: `.venv/bin/python` (3.11.14). Always use it; run pip with `--index-url https://pypi.org/simple/` (private mirror 403s; venv `pip.conf` already pins public PyPI).
- Sentinel model: `gemini-3.5-flash`. Live voice model: `gemini-3.1-flash-live-preview`. Never use `gemini-3.5-flash` for live audio (text-only).
- Audio: input PCM 16-bit **16000 Hz** mono, chunk 1024; output PCM 16-bit **24000 Hz** mono. Live input mime `audio/pcm;rate=16000`.
- SSE event shape unchanged: `{stage, agent, message, status, mode}`. Agent names limited to `Antigravity`, `Computer Use`, `Live Voice`, `Omni`. Sentinel → `Omni`; voice → `Live Voice`.
- API key env var is `GEMINI_AI_KEY` (note `_AI_`). The `AQ.` token expires ~1h.
- Demo must never hard-fail: every external/hardware dependency has a graceful fallback (see spec §8).
- Run pytest with `.venv/bin/python -m pytest`. Async tests auto-run via `pytest.ini` (`asyncio_mode=auto`).
- Working directory for commands: repo root `/Users/boddepalli.madhavi/Downloads/KAVACH-DEEPMIND-v2` unless a step says otherwise.

## File Structure

- Create `backend/audio.py` — mic/speaker PCM I/O + earphone detection.
- Create `backend/agents/sentinel.py` — chunked Flash distress listener.
- Create `backend/agents/voice.py` — Gemini Live voice companion.
- Create `backend/monitor.py` — sentinel→CodeRed→voice lifecycle glue.
- Create `backend/live_location.py` — in-memory live-position store.
- Modify `backend/config.py` — new model/voice/demo constants.
- Modify `backend/orchestrator.py` — `close` flag + return incident context + pass `live_url` to Comms.
- Modify `backend/agents/comms.py` — accept `live_url`, include in SMS.
- Modify `kavach/server.py` — `start_monitor`/`stop_monitor` actions, `/live/*` endpoints, monitor wiring, live-location token.
- Create `kavach/web/live.html` — Leaflet tracker page.
- Modify `kavach/web/index.html` + `kavach/web/app.js` — Start Monitoring control, monitoring panel, geolocation posting.
- Create `tests/` — pytest suite (`conftest.py`, per-module tests).
- Create `scripts/live_smoke.py` — manual Live API bidi smoke test.
- Modify `requirements.txt` — add `pyaudio`; add dev `pytest`, `pytest-asyncio`.

---

### Task 1: Project setup — git, deps, config, test harness

**Files:**
- Modify: `requirements.txt`
- Modify: `backend/config.py`
- Create: `pytest.ini`
- Create: `tests/__init__.py`
- Create: `tests/conftest.py`
- Create: `tests/test_config.py`

**Interfaces:**
- Produces: `config.MODEL_SENTINEL: str`, `config.MODEL_LIVE_VOICE: str`, `config.LIVE_VOICE_NAME: str`, `config.SENTINEL_CHUNK_SECONDS: int`, `config.SENTINEL_LEVEL_TRIGGER: str`, `config.SIMULATE_DISTRESS: bool`, `config.force_earphone() -> Optional[bool]`.
- Produces test helper: `tests/conftest.py::drain(bus) -> list[dict]` (sync, non-blocking drain of an `EventBus` queue).

- [ ] **Step 1: Initialize git (if needed)**

Run:
```bash
cd /Users/boddepalli.madhavi/Downloads/KAVACH-DEEPMIND-v2
git rev-parse --is-inside-work-tree 2>/dev/null || git init
printf '.venv/\n__pycache__/\n*.pyc\n.pytest_cache/\n' >> .gitignore
git add -A && git commit -m "chore: baseline before ambient-voice feature" || echo "nothing to commit"
```
Expected: a git repo exists; baseline committed.

- [ ] **Step 2: Add dependencies**

Edit `requirements.txt`, append:
```
pyaudio>=0.2.14             # mic capture + speaker playback (Live audio)
pytest>=8.0.0              # dev: tests
pytest-asyncio>=0.23.0     # dev: async tests
```

Install (PortAudio is a PyAudio build dep on macOS):
```bash
brew install portaudio
.venv/bin/pip install --index-url https://pypi.org/simple/ "pyaudio>=0.2.14" "pytest>=8.0.0" "pytest-asyncio>=0.23.0"
.venv/bin/python -c "import pyaudio, pytest; print('deps ok')"
```
Expected: `deps ok`. If PyAudio build fails, note it — sentinel/voice fall back to simulation (Task 6/7), tests still pass.

- [ ] **Step 3: Add pytest config**

Create `pytest.ini`:
```ini
[pytest]
asyncio_mode = auto
testpaths = tests
```

- [ ] **Step 4: Write the failing config test**

Create `tests/__init__.py` (empty). Create `tests/test_config.py`:
```python
from backend import config


def test_new_model_constants_present():
    assert config.MODEL_SENTINEL == "gemini-3.5-flash"
    assert config.MODEL_LIVE_VOICE == "gemini-3.1-flash-live-preview"
    assert config.LIVE_VOICE_NAME
    assert isinstance(config.SENTINEL_CHUNK_SECONDS, int)
    assert config.SENTINEL_LEVEL_TRIGGER in {"LOW", "MED", "HIGH"}
    assert isinstance(config.SIMULATE_DISTRESS, bool)


def test_force_earphone_override(monkeypatch):
    monkeypatch.setenv("KAVACH_FORCE_EARPHONE", "true")
    import importlib
    importlib.reload(config)
    assert config.force_earphone() is True
    monkeypatch.setenv("KAVACH_FORCE_EARPHONE", "false")
    importlib.reload(config)
    assert config.force_earphone() is False
    monkeypatch.delenv("KAVACH_FORCE_EARPHONE", raising=False)
    importlib.reload(config)
    assert config.force_earphone() is None
```

- [ ] **Step 5: Run test to verify it fails**

Run: `.venv/bin/python -m pytest tests/test_config.py -v`
Expected: FAIL — `AttributeError: module 'backend.config' has no attribute 'MODEL_SENTINEL'`.

- [ ] **Step 6: Add config constants**

In `backend/config.py`, after the existing model IDs block (after `MODEL_COMPUTER_USE`), add:
```python
# Ambient sentinel + live voice.
MODEL_SENTINEL = os.getenv("KAVACH_MODEL_SENTINEL", "gemini-3.5-flash")
MODEL_LIVE_VOICE = os.getenv("KAVACH_MODEL_LIVE_VOICE", "gemini-3.1-flash-live-preview")
LIVE_VOICE_NAME = os.getenv("KAVACH_LIVE_VOICE_NAME", "Kore")
SENTINEL_CHUNK_SECONDS = int(os.getenv("KAVACH_SENTINEL_CHUNK_SECONDS", "4"))
SENTINEL_LEVEL_TRIGGER = os.getenv("KAVACH_SENTINEL_LEVEL_TRIGGER", "HIGH").upper()
SIMULATE_DISTRESS = os.getenv("KAVACH_SIMULATE_DISTRESS", "false").lower() == "true"
```

Then, after `require_api_key`, add:
```python
def force_earphone():
    """Optional demo override for earphone detection. None = auto-detect."""
    val = os.getenv("KAVACH_FORCE_EARPHONE")
    if val is None:
        return None
    return val.strip().lower() == "true"
```

- [ ] **Step 7: Add the shared test drain helper**

Create `tests/conftest.py`:
```python
import queue as _q

import pytest

from backend.events import EventBus


def drain(bus: EventBus) -> list[dict]:
    """Non-blocking: pull all events currently queued on the bus."""
    out = []
    while True:
        try:
            item = bus._queue.get_nowait()
        except Exception:
            break
        if item is None:
            break
        out.append(item)
    return out


@pytest.fixture
def make_bus():
    def _make():
        return EventBus(mode="online")
    return _make
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_config.py -v`
Expected: PASS (2 passed).

- [ ] **Step 9: Commit**

```bash
git add requirements.txt backend/config.py pytest.ini tests/
git commit -m "feat: config constants + test harness for ambient-voice"
```

---

### Task 2: Audio I/O + earphone detection (`backend/audio.py`)

**Files:**
- Create: `backend/audio.py`
- Create: `tests/test_audio.py`

**Interfaces:**
- Produces: `audio.AVAILABLE: bool`; `audio.MIC_RATE=16000`, `audio.SPK_RATE=24000`, `audio.CHANNELS=1`, `audio.CHUNK=1024`.
- Produces: `audio._looks_like_earphone(name: str) -> bool`; `audio._parse_default_output(profiler_text: str) -> str`; `audio.earphone_connected() -> bool`.
- Produces: `class AudioIn` with `async def chunks(self) -> AsyncIterator[bytes]` and `def stop(self)`; `class AudioOut` with `def play(self, pcm: bytes)` and `def close(self)`.

- [ ] **Step 1: Write failing tests for the pure parsers**

Create `tests/test_audio.py`:
```python
from backend import audio

SAMPLE_HEADPHONES = """
Audio:
    Devices:
        MacBook Pro Speakers:
          Default Output Device: Spam
        AirPods Pro:
          Default Output Device: Yes
          Transport: Bluetooth
"""

SAMPLE_SPEAKERS = """
Audio:
    Devices:
        MacBook Pro Speakers:
          Default Output Device: Yes
        External Microphone:
          Default Input Device: Yes
"""


def test_looks_like_earphone_true():
    assert audio._looks_like_earphone("AirPods Pro")
    assert audio._looks_like_earphone("Bose QuietComfort Headphones")
    assert audio._looks_like_earphone("USB-C Earbuds")


def test_looks_like_earphone_false():
    assert not audio._looks_like_earphone("MacBook Pro Speakers")
    assert not audio._looks_like_earphone("Studio Display Speakers")


def test_parse_default_output_headphones():
    assert audio._parse_default_output(SAMPLE_HEADPHONES) == "AirPods Pro"


def test_parse_default_output_speakers():
    assert audio._parse_default_output(SAMPLE_SPEAKERS) == "MacBook Pro Speakers"
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.venv/bin/python -m pytest tests/test_audio.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'backend.audio'`.

- [ ] **Step 3: Implement `backend/audio.py`**

Create `backend/audio.py`:
```python
"""Laptop audio I/O (PyAudio) + earphone detection for Kavach.

Input: PCM 16-bit mono 16kHz (for Gemini Live / sentinel).
Output: PCM 16-bit mono 24kHz (Live model native audio).
If PyAudio/PortAudio is unavailable, AVAILABLE is False and callers degrade.
"""
from __future__ import annotations

import asyncio
import re
import subprocess
from typing import AsyncIterator

from . import config

MIC_RATE = 16000
SPK_RATE = 24000
CHANNELS = 1
CHUNK = 1024

try:
    import pyaudio  # type: ignore

    _FORMAT = pyaudio.paInt16
    AVAILABLE = True
except Exception:  # noqa: BLE001 — missing PortAudio must not crash import
    pyaudio = None  # type: ignore
    _FORMAT = None
    AVAILABLE = False


_EARPHONE_WORDS = ("airpod", "headphone", "headset", "earbud", "earphone", "buds")


def _looks_like_earphone(name: str) -> bool:
    n = name.lower()
    return any(w in n for w in _EARPHONE_WORDS)


def _parse_default_output(profiler_text: str) -> str:
    """Return the device name whose block contains 'Default Output Device: Yes'."""
    current = ""
    for line in profiler_text.splitlines():
        stripped = line.strip()
        if stripped.endswith(":") and "Default" not in stripped and "Transport" not in stripped:
            current = stripped[:-1].strip()
        elif re.search(r"Default Output Device:\s*Yes", stripped):
            return current
    return ""


def earphone_connected() -> bool:
    """OS-level earphone detection (macOS). Honors KAVACH_FORCE_EARPHONE."""
    override = config.force_earphone()
    if override is not None:
        return override
    try:
        text = subprocess.check_output(
            ["system_profiler", "SPAudioDataType"], text=True, timeout=5
        )
    except Exception:  # noqa: BLE001
        return False
    return _looks_like_earphone(_parse_default_output(text))


class AudioIn:
    """Async mic capture yielding raw PCM chunks (16kHz mono)."""

    def __init__(self) -> None:
        self._stop = False
        self._pa = pyaudio.PyAudio() if AVAILABLE else None
        self._stream = (
            self._pa.open(
                format=_FORMAT, channels=CHANNELS, rate=MIC_RATE,
                input=True, frames_per_buffer=CHUNK,
            )
            if self._pa
            else None
        )

    async def chunks(self) -> AsyncIterator[bytes]:
        if not self._stream:
            return
        while not self._stop:
            data = await asyncio.to_thread(
                self._stream.read, CHUNK, False
            )
            yield data

    def stop(self) -> None:
        self._stop = True
        if self._stream:
            self._stream.stop_stream()
            self._stream.close()
        if self._pa:
            self._pa.terminate()


class AudioOut:
    """Speaker playback of raw PCM (24kHz mono)."""

    def __init__(self) -> None:
        self._pa = pyaudio.PyAudio() if AVAILABLE else None
        self._stream = (
            self._pa.open(
                format=_FORMAT, channels=CHANNELS, rate=SPK_RATE, output=True
            )
            if self._pa
            else None
        )

    def play(self, pcm: bytes) -> None:
        if self._stream:
            self._stream.write(pcm)

    def close(self) -> None:
        if self._stream:
            self._stream.stop_stream()
            self._stream.close()
        if self._pa:
            self._pa.terminate()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_audio.py -v`
Expected: PASS (4 passed).

- [ ] **Step 5: Manual audio smoke (optional, needs mic/speaker)**

Run:
```bash
.venv/bin/python -c "
from backend import audio
print('AVAILABLE:', audio.AVAILABLE)
print('earphone_connected:', audio.earphone_connected())
"
```
Expected: prints booleans without crashing (True/False depends on hardware).

- [ ] **Step 6: Commit**

```bash
git add backend/audio.py tests/test_audio.py
git commit -m "feat: audio I/O + earphone detection"
```

---

### Task 3: Live API bidi smoke script (`scripts/live_smoke.py`)

**Files:**
- Create: `scripts/live_smoke.py`

**Interfaces:**
- Consumes: `config.MODEL_LIVE_VOICE`, `config.require_api_key`, `audio` constants.
- Produces: a runnable manual verification script (no unit test — needs network + key + audio).

- [ ] **Step 1: Write the smoke script**

Create `scripts/live_smoke.py`:
```python
"""Manual smoke test: connect Gemini Live, send silence, confirm frames.

Run: .venv/bin/python scripts/live_smoke.py
Verifies the live model is reachable on the current key and returns audio +
transcript frames. Does NOT require a working mic (sends silence).
"""
import asyncio
import sys

sys.path.insert(0, ".")

from google import genai
from google.genai import types

from backend import config


async def main() -> None:
    client = genai.Client(api_key=config.require_api_key())
    cfg = {
        "response_modalities": ["AUDIO"],
        "system_instruction": "You are a calm safety companion. Say a short hello.",
        "output_audio_transcription": {},
        "input_audio_transcription": {},
    }
    print("connecting", config.MODEL_LIVE_VOICE, "...")
    async with client.aio.live.connect(model=config.MODEL_LIVE_VOICE, config=cfg) as session:
        # 0.5s of 16kHz silence
        silence = b"\x00\x00" * 8000
        await session.send_realtime_input(
            audio=types.Blob(data=silence, mime_type="audio/pcm;rate=16000")
        )
        await session.send_realtime_input(text="Say a one-sentence hello.")
        audio_bytes = 0
        got_text = ""
        async for resp in session.receive():
            sc = resp.server_content
            if sc and sc.model_turn:
                for part in sc.model_turn.parts or []:
                    if part.inline_data and part.inline_data.data:
                        audio_bytes += len(part.inline_data.data)
            if sc and sc.output_transcription and sc.output_transcription.text:
                got_text += sc.output_transcription.text
            if sc and sc.turn_complete:
                break
        print("audio bytes received:", audio_bytes)
        print("transcript:", got_text.strip())
        assert audio_bytes > 0, "no audio received"


if __name__ == "__main__":
    asyncio.run(main())
```

- [ ] **Step 2: Run the smoke test**

Run: `.venv/bin/python scripts/live_smoke.py`
Expected: prints `audio bytes received: <N>` (N>0) and a short transcript. If it 401s, re-mint the `AQ.` token. If the model name errors, list live models via `client.aio.models.list()` and update `KAVACH_MODEL_LIVE_VOICE`.

- [ ] **Step 3: Commit**

```bash
git add scripts/live_smoke.py
git commit -m "test: Gemini Live bidi smoke script"
```

---

### Task 4: Sentinel distress listener (`backend/agents/sentinel.py`)

**Files:**
- Create: `backend/agents/sentinel.py`
- Create: `tests/test_sentinel.py`

**Interfaces:**
- Consumes: `EventBus.emit`, `config.SAFEWORD`, `config.MODEL_SENTINEL`, `config.SENTINEL_LEVEL_TRIGGER`, `config.SIMULATE_DISTRESS`, `audio.AudioIn`, `audio.AVAILABLE`, `audio.MIC_RATE`.
- Produces: `@dataclass SentinelResult(transcript: str, distress: bool, level: str, reason: str)`.
- Produces: `sentinel._parse_classification(text: str) -> SentinelResult`.
- Produces: `sentinel._is_distress(transcript: str, result: SentinelResult) -> bool`.
- Produces: `async def sentinel.run_sentinel(bus: EventBus, stop_event: asyncio.Event) -> Optional[SentinelResult]` — returns the triggering result on distress, or `None` if stopped.

- [ ] **Step 1: Write failing tests**

Create `tests/test_sentinel.py`:
```python
import asyncio

from backend.agents import sentinel
from backend import config
from tests.conftest import drain
from backend.events import EventBus


def test_parse_classification_valid():
    r = sentinel._parse_classification(
        '```json\n{"transcript":"help me","distress":true,'
        '"level":"HIGH","reason":"cry for help"}\n```'
    )
    assert r.transcript == "help me"
    assert r.distress is True
    assert r.level == "HIGH"


def test_parse_classification_garbage_is_safe():
    r = sentinel._parse_classification("not json at all")
    assert r.distress is False
    assert r.level == "LOW"


def test_is_distress_safeword(monkeypatch):
    monkeypatch.setattr(config, "SAFEWORD", "my battery is at 2 percent")
    r = sentinel.SentinelResult("uh my battery is at 2 percent ok", False, "LOW", "")
    assert sentinel._is_distress(r.transcript, r) is True


def test_is_distress_high_level(monkeypatch):
    monkeypatch.setattr(config, "SENTINEL_LEVEL_TRIGGER", "HIGH")
    r = sentinel.SentinelResult("someone is following me", True, "HIGH", "threat")
    assert sentinel._is_distress(r.transcript, r) is True


def test_is_distress_negative(monkeypatch):
    monkeypatch.setattr(config, "SENTINEL_LEVEL_TRIGGER", "HIGH")
    r = sentinel.SentinelResult("nice weather today", False, "LOW", "calm")
    assert sentinel._is_distress(r.transcript, r) is False


def test_run_sentinel_simulated(monkeypatch):
    monkeypatch.setattr(config, "SIMULATE_DISTRESS", True)
    bus = EventBus(mode="online")
    stop = asyncio.Event()
    result = asyncio.run(sentinel.run_sentinel(bus, stop))
    assert result is not None and result.distress is True
    events = drain(bus)
    assert any(e["agent"] == "Omni" for e in events)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.venv/bin/python -m pytest tests/test_sentinel.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'backend.agents.sentinel'`.

- [ ] **Step 3: Implement `backend/agents/sentinel.py`**

Create `backend/agents/sentinel.py`:
```python
"""Sentinel Agent — ambient distress listener (gemini-3.5-flash).

Captures rolling mic chunks, transcribes + classifies distress, streams the
transcript/threat to the UI as `Omni` events, and returns the triggering
result the moment distress (safeword or HIGH) is detected. Degrades to a
simulated trigger when audio/model is unavailable so the demo still advances.
"""
from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass
from typing import Optional

from .. import audio, config
from ..events import AGENT_VERIFY, EventBus

STAGE = "Monitoring"
_LEVELS = {"LOW": 0, "MED": 1, "HIGH": 2}

_PROMPT = (
    "You are Kavach's ambient safety sentinel. Given a short audio clip of a "
    "person's surroundings, transcribe any speech and judge whether the person "
    "is in danger or distress (threats, pleas for help, aggression, fear). "
    'Respond ONLY with compact JSON: {"transcript":"...","distress":true/false,'
    '"level":"LOW|MED|HIGH","reason":"short"}'
)


@dataclass
class SentinelResult:
    transcript: str
    distress: bool
    level: str
    reason: str


def _parse_classification(text: str) -> SentinelResult:
    start, end = text.find("{"), text.rfind("}")
    if start != -1 and end > start:
        try:
            d = json.loads(text[start : end + 1])
            return SentinelResult(
                transcript=str(d.get("transcript", "")),
                distress=bool(d.get("distress", False)),
                level=str(d.get("level", "LOW")).upper(),
                reason=str(d.get("reason", "")),
            )
        except json.JSONDecodeError:
            pass
    return SentinelResult("", False, "LOW", "unparseable")


def _is_distress(transcript: str, result: SentinelResult) -> bool:
    if config.SAFEWORD and config.SAFEWORD.lower() in (transcript or "").lower():
        return True
    trigger = _LEVELS.get(config.SENTINEL_LEVEL_TRIGGER, 2)
    return result.distress and _LEVELS.get(result.level, 0) >= trigger


async def _classify_chunk(pcm: bytes) -> SentinelResult:
    from google import genai
    from google.genai import types

    client = genai.Client(api_key=config.require_api_key())
    resp = await client.aio.models.generate_content(
        model=config.MODEL_SENTINEL,
        contents=[
            _PROMPT,
            types.Part(inline_data=types.Blob(
                mime_type="audio/pcm;rate=16000", data=pcm)),
        ],
    )
    return _parse_classification(resp.text or "")


async def _simulate(bus: EventBus) -> SentinelResult:
    await bus.emit(STAGE, AGENT_VERIFY, "Listening to surroundings… (simulated)")
    await asyncio.sleep(1.5)
    await bus.emit(
        STAGE, AGENT_VERIFY,
        f"Distress detected (simulated): \"{config.SAFEWORD}\".", "active",
    )
    return SentinelResult(config.SAFEWORD, True, "HIGH", "simulated")


async def run_sentinel(
    bus: EventBus, stop_event: asyncio.Event
) -> Optional[SentinelResult]:
    await bus.emit(STAGE, AGENT_VERIFY, "Sentinel active — monitoring for distress.")

    if config.SIMULATE_DISTRESS or not audio.AVAILABLE or not config.GEMINI_API_KEY:
        return await _simulate(bus)

    mic = audio.AudioIn()
    buf = bytearray()
    target = audio.MIC_RATE * 2 * config.SENTINEL_CHUNK_SECONDS  # 16-bit mono
    try:
        async for chunk in mic.chunks():
            if stop_event.is_set():
                return None
            buf.extend(chunk)
            if len(buf) < target:
                continue
            window, buf = bytes(buf), bytearray()
            try:
                result = await _classify_chunk(window)
            except Exception as exc:  # noqa: BLE001
                await bus.emit(STAGE, AGENT_VERIFY,
                               f"Sentinel classify error ({type(exc).__name__}).")
                continue
            if result.transcript:
                await bus.emit(STAGE, AGENT_VERIFY,
                               f"Heard: \"{result.transcript}\" · threat {result.level}")
            if _is_distress(result.transcript, result):
                await bus.emit(STAGE, AGENT_VERIFY,
                               f"DISTRESS confirmed — {result.reason}", "active")
                return result
    finally:
        mic.stop()
    return None
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_sentinel.py -v`
Expected: PASS (6 passed).

- [ ] **Step 5: Commit**

```bash
git add backend/agents/sentinel.py tests/test_sentinel.py
git commit -m "feat: sentinel distress listener"
```

---

### Task 5: Orchestrator split — `resolve_incident()` + `live_url`

> NOTE: `backend/orchestrator.py` was edited after the plan was written. It now
> has `run_code_red(bus, lat, lng) -> None` which, after coordinating agents,
> runs an infinite `_monitor(bus, safe_zone)` threat-rescan loop until the task
> is cancelled, then closes the bus in `finally`. `ActionResult` now has `.eta`.
> This task PRESERVES that behavior and the `_monitor` loop. Do NOT remove the
> loop or the eta usage.

**Files:**
- Modify: `backend/orchestrator.py`
- Modify: `backend/agents/comms.py`
- Create: `tests/test_orchestrator.py`

**Interfaces:**
- Consumes: existing `run_action_agent`, `run_comms_agent`, `run_verification_agent`, `_monitor`.
- Produces (new): `async def resolve_incident(bus, lat=None, lng=None, live_url=None) -> dict` — runs Action + Verification + Comms, returns `{"location_text","maps_url","safe_zone","threat_level","live_url","eta"}`. Does NOT run `_monitor` and does NOT close the bus.
- Produces (changed): `async def run_code_red(bus, lat=None, lng=None, live_url=None) -> None` — `ctx = await resolve_incident(...)`, then `await _monitor(bus, ctx["safe_zone"])`, `finally: await bus.close()`. Keeps the continuous monitor + bus close.
- Produces (changed): `async def run_comms_agent(bus, location_text, maps_url=None, live_url=None) -> CommsResult`.

- [ ] **Step 1: Write failing test**

Create `tests/test_orchestrator.py`:
```python
import asyncio

import backend.orchestrator as orch
from backend.events import EventBus
from backend.agents.action import ActionResult
from backend.agents.verification import VerificationResult
from backend.agents.comms import CommsResult
from tests.conftest import drain


def _stub(monkeypatch):
    async def fake_action(bus, lat=None, lng=None):
        r = ActionResult(True, "12.9,77.5", "http://maps/x", "PS", "ok")
        return r

    async def fake_verify(bus, location_text, lat=None, lng=None):
        return VerificationResult("HIGH", 90, "ctx", False)

    captured = {}

    async def fake_comms(bus, location_text, maps_url=None, live_url=None):
        captured["live_url"] = live_url
        return CommsResult([], [], True, "done")

    monkeypatch.setattr(orch, "run_action_agent", fake_action)
    monkeypatch.setattr(orch, "run_verification_agent", fake_verify)
    monkeypatch.setattr(orch, "run_comms_agent", fake_comms)
    return captured


def test_resolve_incident_returns_context(monkeypatch):
    _stub(monkeypatch)
    bus = EventBus(mode="online")
    ctx = asyncio.run(orch.resolve_incident(bus, live_url="http://live/t"))
    assert ctx["maps_url"] == "http://maps/x"
    assert ctx["safe_zone"] == "PS"
    assert ctx["threat_level"] == "HIGH"
    assert ctx["live_url"] == "http://live/t"


def test_resolve_incident_does_not_close_bus(monkeypatch):
    _stub(monkeypatch)
    bus = EventBus(mode="online")
    asyncio.run(orch.resolve_incident(bus))
    events = drain(bus)
    assert events  # emitted progress
    # No None sentinel was queued (bus stays open for the caller).
    assert bus._queue.qsize() == 0


def test_comms_receives_live_url(monkeypatch):
    captured = _stub(monkeypatch)
    bus = EventBus(mode="online")
    asyncio.run(orch.resolve_incident(bus, live_url="http://live/abc"))
    assert captured["live_url"] == "http://live/abc"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.venv/bin/python -m pytest tests/test_orchestrator.py -v`
Expected: FAIL — `AttributeError: module 'backend.orchestrator' has no attribute 'resolve_incident'`.

- [ ] **Step 3: Update `run_comms_agent` signature (comms.py)**

In `backend/agents/comms.py`, add a `live_url` parameter and include it in the SMS.

Change the `run_comms_agent` signature:
```python
async def run_comms_agent(
    bus: EventBus,
    location_text: str,
    maps_url: Optional[str] = None,
) -> CommsResult:
```
to:
```python
async def run_comms_agent(
    bus: EventBus,
    location_text: str,
    maps_url: Optional[str] = None,
    live_url: Optional[str] = None,
) -> CommsResult:
```

Change `_crisis_sms`:
```python
def _crisis_sms(location_text: str, maps_url: Optional[str]) -> str:
    body = (
        f"KAVACH SILENT ALARM: {config.USER_NAME} triggered a distress signal. "
        f"Location: {location_text}."
    )
    if maps_url:
        body += f" Map: {maps_url}"
    body += " A cab has been dispatched. Please respond immediately."
    return body
```
to:
```python
def _crisis_sms(location_text: str, maps_url: Optional[str],
                live_url: Optional[str] = None) -> str:
    body = (
        f"KAVACH SILENT ALARM: {config.USER_NAME} triggered a distress signal. "
        f"Location: {location_text}."
    )
    if live_url:
        body += f" LIVE location: {live_url}"
    if maps_url:
        body += f" Nearest police: {maps_url}"
    body += " A cab has been dispatched. Please respond immediately."
    return body
```

Thread `live_url` through the call sites:
- In `run_comms_agent`: both `return await _simulate(bus, contacts, location_text, maps_url)` → add `, live_url`; and the `asyncio.to_thread(_twilio_dispatch, bus, loop, contacts, location_text, maps_url)` → add `, live_url`.
- `_twilio_dispatch`: add `live_url: Optional[str]` as the last param; change `body = _crisis_sms(location_text, maps_url)` → `body = _crisis_sms(location_text, maps_url, live_url)`.
- `_simulate`: add `live_url: Optional[str] = None` as the last param; change `body = _crisis_sms(location_text, maps_url)` → `body = _crisis_sms(location_text, maps_url, live_url)`.

- [ ] **Step 4: Split `run_code_red` into `resolve_incident` + `run_code_red` (orchestrator.py)**

In `backend/orchestrator.py`, replace the current `run_code_red` function body (the one that coordinates agents then calls `await _monitor(...)`) with a `resolve_incident` function that returns context, plus a thin `run_code_red` that calls it then runs `_monitor`. Keep the existing `_monitor` function and the `eta_txt` line unchanged.

Replace the whole `async def run_code_red(...)` definition (from `async def run_code_red(` down to just before `async def _monitor(`) with:
```python
async def resolve_incident(
    bus: EventBus,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
    live_url: Optional[str] = None,
) -> dict:
    """Coordinate Action + Verification + Comms; return incident context.

    Does NOT run the continuous monitor and does NOT close the bus — the
    caller (run_code_red or the sentinel Monitor) owns lifecycle after this.
    """
    lat = lat if lat is not None else config.DEFAULT_LAT
    lng = lng if lng is not None else config.DEFAULT_LNG
    location_text = f"{lat:.4f}, {lng:.4f}"

    await bus.emit(
        STAGE, AGENT_ORCHESTRATOR,
        "CODE RED classified — silent distress confirmed. Spawning "
        "Verification, Action and Comms agents.",
    )
    verify_task = asyncio.create_task(
        run_verification_agent(bus, location_text, lat, lng)
    )
    action_task = asyncio.create_task(run_action_agent(bus, lat, lng))

    await bus.emit(
        STAGE, AGENT_ORCHESTRATOR,
        "Agents running in parallel — resolving safe route and threat level.",
    )

    action_result = await action_task
    comms_task = asyncio.create_task(
        run_comms_agent(
            bus,
            location_text=action_result.location_text,
            maps_url=action_result.maps_url,
            live_url=live_url,
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
    return {
        "location_text": location_text,
        "maps_url": action_result.maps_url,
        "safe_zone": action_result.safe_zone,
        "threat_level": verify_result.threat_level,
        "live_url": live_url,
        "eta": action_result.eta,
    }


async def run_code_red(
    bus: EventBus,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
    live_url: Optional[str] = None,
) -> None:
    """Full standalone Code Red: resolve, then monitor until resolved."""
    try:
        ctx = await resolve_incident(bus, lat, lng, live_url)
        await _monitor(bus, ctx["safe_zone"])
    except Exception as exc:  # noqa: BLE001 — never leave the UI hanging
        await bus.emit(
            STAGE, AGENT_ORCHESTRATOR,
            f"Orchestrator error ({type(exc).__name__}) — response degraded but "
            "active.",
            status="failed",
        )
    finally:
        await bus.close()
```

Leave `async def _monitor(bus, safe_zone)` exactly as it is.

- [ ] **Step 5: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_orchestrator.py tests/test_config.py -v`
Expected: PASS (all).

- [ ] **Step 6: Commit**

```bash
git add backend/orchestrator.py backend/agents/comms.py tests/test_orchestrator.py
git commit -m "refactor: split resolve_incident from run_code_red + live_url in comms"
```

---

### Task 6: Monitor lifecycle glue (`backend/monitor.py`)

**Files:**
- Create: `backend/monitor.py`
- Create: `tests/test_monitor.py`

**Interfaces:**
- Consumes: `run_sentinel` (Task 4), `resolve_incident` + `_monitor` (Task 5), `run_voice_companion` (Task 7), `audio.earphone_connected`, `EventBus`.
- Produces: `class Monitor` with attributes `task: asyncio.Task`, method `stop() -> None`.
- Produces: `def start_monitoring(bus, lat=None, lng=None, live_url=None) -> Monitor`.

Flow inside `Monitor._run()`: await `run_sentinel`; if it returns a result (distress), emit an escalation line, call `resolve_incident(...)` to get context, then run `_monitor(bus, ctx["safe_zone"])` AND (if `earphone_connected()`) `run_voice_companion(bus, ctx)` **concurrently** via `asyncio.gather`. Cancellation (server calls `stop()` + cancels the task on Resolve) ends everything; bus closed in `finally`.

- [ ] **Step 1: Write failing test**

Create `tests/test_monitor.py`:
```python
import asyncio

import backend.monitor as monitor
from backend.events import EventBus
from backend.agents.sentinel import SentinelResult


def test_monitor_distress_resolves_and_runs_voice(monkeypatch):
    calls = {"resolve": False, "voice": False}

    async def fake_sentinel(bus, stop_event):
        return SentinelResult("help", True, "HIGH", "x")

    async def fake_resolve(bus, lat=None, lng=None, live_url=None):
        calls["resolve"] = True
        return {"safe_zone": "PS", "maps_url": "u", "location_text": "l",
                "threat_level": "HIGH", "live_url": live_url, "eta": None}

    async def fake_threat_monitor(bus, safe_zone):
        return  # finite for the test

    async def fake_voice(bus, context):
        calls["voice"] = True

    monkeypatch.setattr(monitor, "run_sentinel", fake_sentinel)
    monkeypatch.setattr(monitor, "resolve_incident", fake_resolve)
    monkeypatch.setattr(monitor, "_monitor", fake_threat_monitor)
    monkeypatch.setattr(monitor, "run_voice_companion", fake_voice)
    monkeypatch.setattr(monitor, "earphone_connected", lambda: True)

    async def go():
        bus = EventBus(mode="online")
        m = monitor.start_monitoring(bus, live_url="http://live/t")
        await m.task

    asyncio.run(go())
    assert calls["resolve"] is True
    assert calls["voice"] is True


def test_monitor_no_earphone_skips_voice(monkeypatch):
    calls = {"voice": False}

    async def fake_sentinel(bus, stop_event):
        return SentinelResult("help", True, "HIGH", "x")

    async def fake_resolve(bus, lat=None, lng=None, live_url=None):
        return {"safe_zone": "PS", "maps_url": "u", "location_text": "l",
                "threat_level": "HIGH", "live_url": live_url, "eta": None}

    async def fake_threat_monitor(bus, safe_zone):
        return

    async def fake_voice(bus, context):
        calls["voice"] = True

    monkeypatch.setattr(monitor, "run_sentinel", fake_sentinel)
    monkeypatch.setattr(monitor, "resolve_incident", fake_resolve)
    monkeypatch.setattr(monitor, "_monitor", fake_threat_monitor)
    monkeypatch.setattr(monitor, "run_voice_companion", fake_voice)
    monkeypatch.setattr(monitor, "earphone_connected", lambda: False)

    async def go():
        bus = EventBus(mode="online")
        m = monitor.start_monitoring(bus)
        await m.task

    asyncio.run(go())
    assert calls["voice"] is False


def test_monitor_stop_before_distress(monkeypatch):
    async def fake_sentinel(bus, stop_event):
        await stop_event.wait()
        return None

    monkeypatch.setattr(monitor, "run_sentinel", fake_sentinel)

    async def go():
        bus = EventBus(mode="online")
        m = monitor.start_monitoring(bus)
        await asyncio.sleep(0.05)
        m.stop()
        await m.task

    asyncio.run(go())  # must not hang or raise
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.venv/bin/python -m pytest tests/test_monitor.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'backend.monitor'`.

- [ ] **Step 3: Implement `backend/monitor.py`**

Create `backend/monitor.py`:
```python
"""Monitor — sentinel → auto Code Red → live voice companion lifecycle.

Runs the sentinel; on distress it coordinates the incident (resolve_incident),
then runs the continuous threat monitor and — if an earphone is connected —
the live voice companion, concurrently, until the session is resolved
(the task is cancelled). Closes the bus at the end.
"""
from __future__ import annotations

import asyncio
from typing import Optional

from .audio import earphone_connected
from .events import AGENT_ORCHESTRATOR, EventBus
from .orchestrator import resolve_incident, _monitor
from .agents.sentinel import run_sentinel
from .agents.voice import run_voice_companion

STAGE = "Monitoring"


class Monitor:
    def __init__(self, bus: EventBus, lat: Optional[float],
                 lng: Optional[float], live_url: Optional[str]) -> None:
        self.bus = bus
        self.lat = lat
        self.lng = lng
        self.live_url = live_url
        self.stop_event = asyncio.Event()
        self.task = asyncio.create_task(self._run())

    def stop(self) -> None:
        self.stop_event.set()

    async def _run(self) -> None:
        try:
            result = await run_sentinel(self.bus, self.stop_event)
            if result is None:  # stopped before any distress
                return
            await self.bus.emit(
                STAGE, AGENT_ORCHESTRATOR,
                "Distress confirmed — escalating to autonomous Code Red.",
            )
            ctx = await resolve_incident(
                self.bus, self.lat, self.lng, live_url=self.live_url
            )
            tasks = [asyncio.create_task(_monitor(self.bus, ctx["safe_zone"]))]
            if earphone_connected():
                await self.bus.emit(
                    STAGE, AGENT_ORCHESTRATOR,
                    "Earphone detected — connecting live voice companion.",
                )
                tasks.append(asyncio.create_task(run_voice_companion(self.bus, ctx)))
            else:
                await self.bus.emit(
                    STAGE, AGENT_ORCHESTRATOR,
                    "No earphone — voice companion on standby.",
                )
            try:
                await asyncio.gather(*tasks)
            finally:
                for t in tasks:
                    t.cancel()
        except asyncio.CancelledError:
            raise
        except Exception as exc:  # noqa: BLE001
            await self.bus.emit(
                STAGE, AGENT_ORCHESTRATOR,
                f"Monitor error ({type(exc).__name__}).", status="failed",
            )
        finally:
            await self.bus.close()


def start_monitoring(
    bus: EventBus,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
    live_url: Optional[str] = None,
) -> Monitor:
    return Monitor(bus, lat, lng, live_url)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_monitor.py -v`
Expected: PASS (3 passed). NOTE: `backend/agents/voice.py` (Task 7) must already exist — this plan runs Task 7 BEFORE Task 6. If it does not exist yet, stop and report NEEDS_CONTEXT.

- [ ] **Step 5: Commit**

```bash
git add backend/monitor.py tests/test_monitor.py
git commit -m "feat: monitor lifecycle glue (sentinel->resolve->voice, concurrent)"
```

---
### Task 7: Live voice companion (`backend/agents/voice.py`)

**Files:**
- Create: `backend/agents/voice.py`
- Create: `tests/test_voice.py`

**Interfaces:**
- Consumes: `config.MODEL_LIVE_VOICE`, `config.LIVE_VOICE_NAME`, `config.require_api_key`, `audio.AudioIn`, `audio.AudioOut`, `audio.AVAILABLE`, `EventBus`.
- Produces: `async def run_voice_companion(bus: EventBus, context: dict) -> None`.
- Produces: `voice._system_instruction(context: dict) -> str`; `async def voice._fallback(bus, context) -> None`.

- [ ] **Step 1: Write failing tests (fallback + prompt)**

Create `tests/test_voice.py`:
```python
import asyncio

from backend.agents import voice
from backend.events import EventBus
from tests.conftest import drain


def test_system_instruction_includes_context():
    s = voice._system_instruction(
        {"safe_zone": "MG Road Police Station", "location_text": "12.9, 77.5",
         "threat_level": "HIGH", "maps_url": "u", "live_url": None}
    )
    assert "MG Road Police Station" in s
    assert "companion" in s.lower() or "calm" in s.lower()


def test_fallback_emits_live_voice_line():
    bus = EventBus(mode="online")
    asyncio.run(voice._fallback(bus, {"safe_zone": "PS", "location_text": "l",
                                      "threat_level": "HIGH", "maps_url": "u",
                                      "live_url": None}))
    events = drain(bus)
    assert any(e["agent"] == "Live Voice" for e in events)


def test_run_voice_companion_falls_back_when_unavailable(monkeypatch):
    monkeypatch.setattr(voice.audio, "AVAILABLE", False)
    bus = EventBus(mode="online")
    asyncio.run(voice.run_voice_companion(bus, {"safe_zone": "PS",
                "location_text": "l", "threat_level": "HIGH", "maps_url": "u",
                "live_url": None}))
    events = drain(bus)
    assert any(e["agent"] == "Live Voice" for e in events)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.venv/bin/python -m pytest tests/test_voice.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'backend.agents.voice'`.

- [ ] **Step 3: Implement `backend/agents/voice.py`**

Create `backend/agents/voice.py`:
```python
"""Live Voice Companion — real-time victim coaching (Gemini Live API).

Opens a native-audio bidi session (gemini-3.1-flash-live-preview) and privately
coaches the victim through the earphone: calm, brief reassurance + step-by-step
guidance until help arrives. Based on the official command-line/python example
(4 async tasks: listen → send → receive → play). Falls back to a single
scripted `Live Voice` line if audio or the live model is unavailable.
"""
from __future__ import annotations

import asyncio

from .. import audio, config
from ..events import AGENT_COMMS, EventBus

STAGE = "Companion"
MAX_SESSION_S = 90  # demo bound (Live cap is 15 min)


def _system_instruction(context: dict) -> str:
    return (
        "You are Kavach, a calm, reassuring personal-safety companion speaking "
        "privately to a person in danger through their earphone. Keep every "
        "reply short (1-2 sentences), warm, and practical. Reassure them help "
        "is on the way, tell them to move toward light and people, and keep "
        "them talking. Do not panic them. Context: their approximate location "
        f"is {context.get('location_text','unknown')}, threat level "
        f"{context.get('threat_level','HIGH')}, nearest safe zone is "
        f"{context.get('safe_zone','the nearest police station')}."
    )


async def _fallback(bus: EventBus, context: dict) -> None:
    await bus.emit(
        STAGE, AGENT_COMMS,
        "Voice companion (text fallback): \"I'm here with you. Help is on the "
        f"way to {context.get('safe_zone','the nearest police station')}. Stay "
        "with me and walk toward the nearest lights.\"",
    )


async def run_voice_companion(bus: EventBus, context: dict) -> None:
    if not audio.AVAILABLE or not config.GEMINI_API_KEY:
        await _fallback(bus, context)
        return
    try:
        await asyncio.wait_for(_live_session(bus, context), timeout=MAX_SESSION_S)
    except Exception as exc:  # noqa: BLE001 — never hard-fail the demo
        await bus.emit(STAGE, AGENT_COMMS,
                       f"Live voice unavailable ({type(exc).__name__}) — fallback.")
        await _fallback(bus, context)


async def _live_session(bus: EventBus, context: dict) -> None:
    from google import genai
    from google.genai import types

    client = genai.Client(api_key=config.require_api_key())
    cfg = {
        "response_modalities": ["AUDIO"],
        "system_instruction": _system_instruction(context),
        "speech_config": {
            "voice_config": {
                "prebuilt_voice_config": {"voice_name": config.LIVE_VOICE_NAME}
            }
        },
        "input_audio_transcription": {},
        "output_audio_transcription": {},
    }

    mic = audio.AudioIn()
    spk = audio.AudioOut()
    out_q: asyncio.Queue[bytes] = asyncio.Queue()
    await bus.emit(STAGE, AGENT_COMMS, "Live voice companion connected.")

    async with client.aio.live.connect(model=config.MODEL_LIVE_VOICE, config=cfg) as session:
        # Kick off the conversation.
        await session.send_realtime_input(
            text="Greet the person calmly and tell them you are here to help."
        )

        async def send_mic():
            async for chunk in mic.chunks():
                await session.send_realtime_input(
                    audio=types.Blob(data=chunk, mime_type="audio/pcm;rate=16000")
                )

        async def play_out():
            while True:
                pcm = await out_q.get()
                await asyncio.to_thread(spk.play, pcm)

        async def receive():
            async for resp in session.receive():
                sc = resp.server_content
                if sc and sc.model_turn:
                    for part in sc.model_turn.parts or []:
                        if part.inline_data and part.inline_data.data:
                            out_q.put_nowait(part.inline_data.data)
                if sc and sc.output_transcription and sc.output_transcription.text:
                    await bus.emit(STAGE, AGENT_COMMS,
                                   f"Companion: {sc.output_transcription.text}")
                if sc and sc.input_transcription and sc.input_transcription.text:
                    await bus.emit(STAGE, AGENT_COMMS,
                                   f"You: {sc.input_transcription.text}")

        tasks = [asyncio.create_task(send_mic()),
                 asyncio.create_task(play_out()),
                 asyncio.create_task(receive())]
        try:
            await asyncio.gather(*tasks)
        finally:
            for t in tasks:
                t.cancel()
            mic.stop()
            spk.close()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_voice.py tests/test_monitor.py -v`
Expected: PASS (all).

- [ ] **Step 5: Manual live-voice smoke (needs mic + earphone)**

Run:
```bash
.venv/bin/python -c "
import asyncio
from backend.events import EventBus
from backend.agents.voice import run_voice_companion
ctx = {'location_text':'12.97,77.59','threat_level':'HIGH','safe_zone':'MG Road Police Station','maps_url':'','live_url':None}
async def go():
    bus = EventBus()
    t = asyncio.create_task(run_voice_companion(bus, ctx))
    async for e in bus.stream():
        print(e['agent'], '|', e['message'][:80])
    await t
asyncio.run(asyncio.wait_for(go(), timeout=95))
"
```
Expected: hear a calm greeting in the earphone; speak and get spoken replies; captions print. Ctrl-C to end.

- [ ] **Step 6: Commit**

```bash
git add backend/agents/voice.py tests/test_voice.py
git commit -m "feat: live voice companion (Gemini Live native audio)"
```

---

### Task 8: Live-location store (`backend/live_location.py`)

**Files:**
- Create: `backend/live_location.py`
- Create: `tests/test_live_location.py`

**Interfaces:**
- Produces: `class LiveLocationStore` with `new_token() -> str`, `update(token: str, lat: float, lng: float) -> bool`, `get(token: str) -> Optional[dict]`, `drop(token: str) -> None`.
- Produces: module singleton `store = LiveLocationStore()`.

- [ ] **Step 1: Write failing tests**

Create `tests/test_live_location.py`:
```python
from backend.live_location import LiveLocationStore


def test_new_token_unique():
    s = LiveLocationStore()
    a, b = s.new_token(), s.new_token()
    assert a != b and len(a) >= 6


def test_update_and_get():
    s = LiveLocationStore()
    t = s.new_token()
    assert s.update(t, 12.34, 56.78) is True
    pos = s.get(t)
    assert pos["lat"] == 12.34 and pos["lng"] == 56.78 and "ts" in pos


def test_update_unknown_token():
    s = LiveLocationStore()
    assert s.update("nope", 1.0, 2.0) is False


def test_get_missing_returns_none():
    s = LiveLocationStore()
    assert s.get("missing") is None


def test_drop():
    s = LiveLocationStore()
    t = s.new_token()
    s.update(t, 1.0, 2.0)
    s.drop(t)
    assert s.get(t) is None
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.venv/bin/python -m pytest tests/test_live_location.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'backend.live_location'`.

- [ ] **Step 3: Implement `backend/live_location.py`**

Create `backend/live_location.py`:
```python
"""In-memory live-location store for self-hosted tracking links.

A Code Red mints a token; the victim's browser POSTs GPS updates; contacts
poll the latest position via the tracker page. Tokens live only for the
session (cleared on resolve).
"""
from __future__ import annotations

import secrets
import time
from typing import Optional


class LiveLocationStore:
    def __init__(self) -> None:
        self._data: dict[str, dict] = {}

    def new_token(self) -> str:
        token = secrets.token_urlsafe(6)
        self._data[token] = {"lat": None, "lng": None, "ts": None}
        return token

    def update(self, token: str, lat: float, lng: float) -> bool:
        if token not in self._data:
            return False
        self._data[token] = {"lat": lat, "lng": lng, "ts": time.time()}
        return True

    def get(self, token: str) -> Optional[dict]:
        return self._data.get(token)

    def drop(self, token: str) -> None:
        self._data.pop(token, None)


store = LiveLocationStore()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_live_location.py -v`
Expected: PASS (5 passed).

- [ ] **Step 5: Commit**

```bash
git add backend/live_location.py tests/test_live_location.py
git commit -m "feat: in-memory live-location store"
```

---

### Task 9: Server wiring — monitor actions + live endpoints

**Files:**
- Modify: `kavach/server.py`
- Create: `kavach/web/live.html`
- Create: `tests/test_server.py`

**Interfaces:**
- Consumes: `start_monitoring`, `Monitor`, `live_location.store`, `EventBus`.
- Produces (HTTP): `POST /trigger` handles `start_monitor`/`stop_monitor`; `GET /live/{token}` (HTML); `POST /live/{token}/update` (`{lat,lng}` → `{ok}`); `GET /live/{token}/pos` (`{lat,lng,ts}` or `{}`).

- [ ] **Step 1: Write failing tests**

Create `tests/test_server.py`:
```python
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "kavach"))

from fastapi.testclient import TestClient
import server


def _client():
    return TestClient(server.app)


def test_live_update_and_pos_roundtrip():
    c = _client()
    token = server.live_store.new_token()
    r = c.post(f"/live/{token}/update", json={"lat": 10.0, "lng": 20.0})
    assert r.status_code == 200 and r.json()["ok"] is True
    r2 = c.get(f"/live/{token}/pos")
    assert r2.json()["lat"] == 10.0 and r2.json()["lng"] == 20.0


def test_live_pos_unknown_token_empty():
    c = _client()
    assert c.get("/live/doesnotexist/pos").json() == {}


def test_live_tracker_page_served():
    c = _client()
    token = server.live_store.new_token()
    r = c.get(f"/live/{token}")
    assert r.status_code == 200 and "leaflet" in r.text.lower()


def test_trigger_mode_switch_still_ok():
    c = _client()
    r = c.post("/trigger", json={"action": "mode_switch", "mode": "offline"})
    assert r.status_code == 200 and r.json()["status"] == "ok"


def test_app_js_served_at_root():
    c = _client()
    assert c.get("/app.js").status_code == 200
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.venv/bin/python -m pytest tests/test_server.py -v`
Expected: FAIL — `AttributeError: module 'server' has no attribute 'live_store'` (and 404s).

- [ ] **Step 3: Wire the server**

In `kavach/server.py`, add imports near the existing backend imports:
```python
from backend.monitor import start_monitoring, Monitor  # noqa: E402
from backend.live_location import store as live_store  # noqa: E402
```

Add `live_url` + monitor to the `Session` class. Replace the `Session` class with:
```python
class Session:
    """One active incident: event bus + orchestrator/monitor task + live token."""

    def __init__(self, bus: EventBus, task=None, monitor: "Monitor | None" = None,
                 token: str | None = None) -> None:
        self.bus = bus
        self.task = task
        self.monitor = monitor
        self.token = token
```

In the `trigger` handler, extend the action dispatch. Replace the block:
```python
    if action == "code_red" and mode != "offline":
        _start_session("1", data)
    elif action == "resolve":
        _end_session("1")
```
with:
```python
    if action == "code_red" and mode != "offline":
        _start_session("1", data)
    elif action == "start_monitor" and mode != "offline":
        _start_monitor_session("1", data)
    elif action in ("resolve", "stop_monitor"):
        _end_session("1")
```

Update `_start_session` to mint a live token and pass `live_url`:
```python
def _start_session(session_id: str, data: dict) -> None:
    _end_session(session_id)
    bus = EventBus(mode="online")
    token = live_store.new_token()
    lat, lng = data.get("lat"), data.get("lng")
    live_url = f"/live/{token}"
    task = asyncio.create_task(
        run_code_red(bus, lat, lng, live_url=live_url)
    )
    _sessions[session_id] = Session(bus, task=task, token=token)
```

Add the monitor starter after `_start_session`:
```python
def _start_monitor_session(session_id: str, data: dict) -> None:
    _end_session(session_id)
    bus = EventBus(mode="online")
    token = live_store.new_token()
    lat, lng = data.get("lat"), data.get("lng")
    live_url = f"/live/{token}"
    monitor = start_monitoring(bus, lat, lng, live_url=live_url)
    _sessions[session_id] = Session(bus, task=monitor.task, monitor=monitor,
                                    token=token)
```

Update `_end_session` to also stop the monitor and drop the token:
```python
def _end_session(session_id: str) -> None:
    session = _sessions.pop(session_id, None)
    if not session:
        return
    if session.monitor:
        session.monitor.stop()
    if session.task and not session.task.done():
        session.task.cancel()
    if session.token:
        live_store.drop(session.token)
```

Add the live-location routes (place them just before the root static mount at the bottom of the file, i.e. before `app.mount("/", ...)`):
```python
from fastapi import Body  # noqa: E402


@app.post("/live/{token}/update")
async def live_update(token: str, body: dict = Body(...)):
    ok = live_store.update(token, float(body["lat"]), float(body["lng"]))
    return {"ok": ok}


@app.get("/live/{token}/pos")
async def live_pos(token: str):
    return live_store.get(token) or {}


@app.get("/live/{token}")
async def live_page(token: str):
    with open(os.path.join(_WEB_DIR, "live.html")) as f:
        html = f.read().replace("__TOKEN__", token)
    return HTMLResponse(html)
```

- [ ] **Step 4: Create the tracker page**

Create `kavach/web/live.html`:
```html
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Kavach — Live Location</title>
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    body { margin: 0; font-family: system-ui, sans-serif; background: #0B0F14; color: #E6EDF3; }
    #bar { padding: 10px 14px; background: #B00020; font-weight: 700; }
    #map { height: calc(100vh - 44px); width: 100%; }
    #status { padding: 6px 14px; font-size: 12px; color: #9BA7B4; }
  </style>
</head>
<body>
  <div id="bar">KAVACH · LIVE LOCATION</div>
  <div id="map"></div>
  <div id="status">Waiting for position…</div>
  <script>
    const token = "__TOKEN__";
    const map = L.map('map').setView([12.9716, 77.5946], 15);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      { attribution: '© OpenStreetMap' }).addTo(map);
    let marker = null;
    async function tick() {
      try {
        const r = await fetch(`/live/${token}/pos`);
        const p = await r.json();
        if (p && p.lat != null && p.lng != null) {
          const ll = [p.lat, p.lng];
          if (!marker) { marker = L.marker(ll).addTo(map); }
          marker.setLatLng(ll);
          map.setView(ll);
          document.getElementById('status').textContent =
            `Live: ${p.lat.toFixed(5)}, ${p.lng.toFixed(5)} · updated ${new Date((p.ts||0)*1000).toLocaleTimeString()}`;
        }
      } catch (e) { /* keep polling */ }
    }
    setInterval(tick, 3000); tick();
  </script>
</body>
</html>
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.venv/bin/python -m pytest tests/test_server.py -v`
Expected: PASS (5 passed).

- [ ] **Step 6: Commit**

```bash
git add kavach/server.py kavach/web/live.html tests/test_server.py
git commit -m "feat: server monitor actions + live-location endpoints + tracker page"
```

---

### Task 10: Frontend — Start Monitoring + geolocation posting

**Files:**
- Modify: `kavach/web/index.html`
- Modify: `kavach/web/app.js`

**Interfaces:**
- Consumes (HTTP): `POST /trigger {action:"start_monitor"}`, `POST /trigger {action:"stop_monitor"}`, `POST /live/{token}/update`.
- Produces: a "Start Monitoring" button that starts SSE + geolocation posting; monitoring events (agent `Omni`, stage `Monitoring`) render in the existing event log.

- [ ] **Step 1: Add the Start Monitoring button (index.html)**

In `kavach/web/index.html`, find the hub view's trigger button (search for `id="triggerBtn"`). Immediately after that button's element, add:
```html
<button id="monitorBtn" class="mt-3 w-full py-3 rounded-xl border border-borderGray text-safetyGreen font-bold tracking-wider">
  START MONITORING
</button>
```

- [ ] **Step 2: Wire monitoring + geolocation (app.js)**

In `kavach/web/app.js`, inside the `DOMContentLoaded` handler near the other `getElementById` lines, add:
```javascript
    const monitorBtn = document.getElementById('monitorBtn');
    let liveToken = null;
    let geoWatchId = null;
```

After the existing `triggerBtn.addEventListener(...)` line, add:
```javascript
    if (monitorBtn) monitorBtn.addEventListener('click', startMonitoring);
```

Add these functions before the closing `});` of the file:
```javascript
    async function startMonitoring() {
        isDefenseActive = true;
        hubView.classList.add('hidden');
        activeView.classList.remove('hidden');
        activeView.classList.add('flex');
        eventLog.innerHTML = '';
        activeTitle.textContent = "SENTINEL MONITORING";
        activeSubtitle.textContent = "LISTENING FOR DISTRESS";
        logEvent('System', 'Ambient monitoring started.', 'safetyGreen', 'hearing');
        try {
            await fetch('/trigger', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: "start_monitor", source: "button" })
            });
            startLiveLocation();
            startSSE();
        } catch (e) {
            console.log("Backend offline, running local mock.");
            runMockSequence();
        }
    }

    function startLiveLocation() {
        liveToken = "1-" + Math.random().toString(36).slice(2, 8);
        // Ask backend for a token via a lightweight update to the session's token
        // (server mints the token; the dashboard just needs to POST GPS to it).
        if (!navigator.geolocation) return;
        geoWatchId = navigator.geolocation.watchPosition(
            (pos) => postLive(pos.coords.latitude, pos.coords.longitude),
            () => postLive(12.9716, 77.5946),  // fallback: default location
            { enableHighAccuracy: true, maximumAge: 2000 }
        );
    }

    async function postLive(lat, lng) {
        // Server exposes the active token via /session token; simplest: the
        // tracker link is surfaced in the event log by Comms. Here we post to
        // the well-known session token endpoint.
        try {
            await fetch(`/live/current/update`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ lat, lng })
            });
        } catch (e) { /* ignore */ }
    }

    function stopLiveLocation() {
        if (geoWatchId != null && navigator.geolocation) {
            navigator.geolocation.clearWatch(geoWatchId);
            geoWatchId = null;
        }
    }
```

In `resolveIncident()`, after `stopSiren();`, add:
```javascript
        stopLiveLocation();
        fetch('/trigger', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: "stop_monitor" })
        }).catch(() => {});
```

- [ ] **Step 3: Add the `/live/current` alias on the server**

The dashboard posts to a stable `/live/current/update` (it does not know the minted token). In `kavach/server.py`, add a helper that maps `current` → the active session's token. Add before the `live_update` route:
```python
def _current_token() -> str | None:
    session = _sessions.get("1")
    return session.token if session else None
```
And update `live_update` and `live_pos` to resolve `current`:
```python
@app.post("/live/{token}/update")
async def live_update(token: str, body: dict = Body(...)):
    if token == "current":
        token = _current_token() or token
    ok = live_store.update(token, float(body["lat"]), float(body["lng"]))
    return {"ok": ok}


@app.get("/live/{token}/pos")
async def live_pos(token: str):
    if token == "current":
        token = _current_token() or token
    return live_store.get(token) or {}
```

- [ ] **Step 4: Manual browser verification**

Run:
```bash
cd /Users/boddepalli.madhavi/Downloads/KAVACH-DEEPMIND-v2/kavach && ../.venv/bin/python server.py
```
In a browser at http://localhost:8000: click **START MONITORING**, allow location. With `KAVACH_SIMULATE_DISTRESS=true` (set before launch) the sentinel auto-fires Code Red; watch the event log escalate. Copy the `/live/<token>` link from the Comms SMS event (or open `/live/current`) in a second tab → marker shows your position and follows `watchPosition`.

Expected: monitoring events → Code Red escalation → live tracker shows position. Resolve returns to hub and stops geolocation.

- [ ] **Step 5: Re-run full test suite**

Run: `.venv/bin/python -m pytest -v`
Expected: PASS (all tasks' tests green).

- [ ] **Step 6: Commit**

```bash
git add kavach/web/index.html kavach/web/app.js kavach/server.py
git commit -m "feat: frontend Start Monitoring + live geolocation posting"
```

---

## Self-Review Notes

- **Spec coverage:** Sentinel (§5.2 → Task 4), auto Code Red glue (§5.4 → Tasks 5,6), voice companion (§5.3 → Task 7), live location (§5.5 → Tasks 8,9,10), server actions (§6 → Task 9), frontend (§7 → Task 10), config (§10 → Task 1), deps (§9 → Task 1), fallbacks (§8 → each task's fallback path + tests), testing (§11 → per-task tests + smoke scripts). Android (§12 phase 7) intentionally deferred to a separate spec.
- **Type consistency:** `SentinelResult(transcript,distress,level,reason)` used identically in Tasks 4/6/7. `run_code_red(bus,lat,lng,live_url,close)->dict` context keys (`location_text,maps_url,safe_zone,threat_level,live_url`) consumed unchanged in Tasks 6/7/9. `run_voice_companion(bus,context)` signature identical in Tasks 6/7. `LiveLocationStore` method names (`new_token/update/get/drop`) identical in Tasks 8/9.
- **Known demo caveat:** `/live/current` maps to the single active session "1" (matches the frontend's hardcoded session). Multi-session is out of scope.
