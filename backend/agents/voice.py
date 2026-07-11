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
