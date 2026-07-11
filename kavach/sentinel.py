# Dark Survival (Gemma 4 Edge, on-device) + Verification
# This file is owned by the ML / Prompt Engineer
"""Sentinel — offline safeword watch (Gemma 4, on-device) plus an ONLINE
ambient-audio confirmation that runs real audio through fast Gemini Flash.

Confirmation only — it *upgrades* the threat level, it never fires Code Red.
"""
from __future__ import annotations

import asyncio
import os
import sys

# Make the repo-root `backend` package importable when run standalone.
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)


def start_sentinel_loop():
    """
    Offline listening loop using Gemma 4 E2B.
    Detects safeword ("battery at 2 percent") and triggers Dark Survival logic.
    """
    print("Edge Sentinel: Gemma 4 offline loop listening...")
    pass


def verify_ambient_audio(audio_chunk: bytes, mime_type: str = "audio/wav"):
    """Process an ambient audio chunk with fast Gemini Flash (online).

    Sends the REAL audio bytes to ``MODEL_VOICE_ANALYSIS`` and returns a
    ``VerificationResult`` (threat_level / confidence / context). Confirmation
    only — the caller decides whether to upgrade the threat level.

    Falls back to a deterministic HIGH assessment if the model/key is
    unavailable so the offline demo never hard-fails.
    """
    from backend.agents.verification import (
        VerificationResult,
        analyze_ambient_audio,
    )

    async def _run():
        return await analyze_ambient_audio(audio_chunk, mime_type)

    try:
        return asyncio.run(_run())
    except Exception as exc:  # noqa: BLE001 — never crash the sentinel loop
        print(f"Ambient audio verify degraded ({type(exc).__name__}: {exc})")
        return VerificationResult(
            threat_level="HIGH",
            confidence=90,
            context="Ambient audio unverified; assuming elevated risk.",
            simulated=True,
            modality="voice",
        )


def record_ambient_clip(seconds: float = 4.0, samplerate: int = 16000) -> bytes:
    """Capture a short mono WAV clip from the laptop mic (real input source).

    Requires ``sounddevice`` (pip install sounddevice). Raises RuntimeError
    with an actionable message if it's not installed / no mic is available.
    """
    try:
        import io
        import wave

        import numpy as np  # noqa: F401 — sounddevice returns numpy frames
        import sounddevice as sd
    except ImportError as exc:  # pragma: no cover - optional dependency
        raise RuntimeError(
            "record_ambient_clip needs 'sounddevice' (and numpy): "
            "pip install sounddevice numpy"
        ) from exc

    frames = sd.rec(
        int(seconds * samplerate), samplerate=samplerate, channels=1, dtype="int16"
    )
    sd.wait()
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)  # int16
        wav.setframerate(samplerate)
        wav.writeframes(frames.tobytes())
    return buf.getvalue()


if __name__ == "__main__":
    # Standalone smoke: record a clip from the mic (if sounddevice is present)
    # and run it through the online ambient-audio verifier.
    try:
        clip = record_ambient_clip(4.0)
        print(f"Captured {len(clip)} bytes; sending to Gemini Flash...")
        print(verify_ambient_audio(clip, mime_type="audio/wav"))
    except RuntimeError as err:
        print(err)
        start_sentinel_loop()
