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
