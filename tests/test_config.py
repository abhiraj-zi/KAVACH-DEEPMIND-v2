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
