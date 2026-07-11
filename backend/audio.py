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
