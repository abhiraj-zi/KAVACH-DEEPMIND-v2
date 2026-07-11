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
