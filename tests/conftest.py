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
