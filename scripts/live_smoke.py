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
