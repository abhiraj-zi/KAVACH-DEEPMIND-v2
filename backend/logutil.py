"""Tiny stdout logger for server-side (terminal) visibility.

The agents stream their user-facing progress to the phone over SSE. This logger
is the *other* channel: developer/demo logs printed to the terminal running the
server — used mainly to show the on-device Gemma (DARK SURVIVAL) path working
locally without cluttering the frontend.

We attach our own StreamHandler(stdout) with ``propagate=False`` so these lines
show up regardless of how uvicorn configures the root logger.
"""
from __future__ import annotations

import logging
import sys

_CONFIGURED: set[str] = set()


def get_logger(name: str = "kavach") -> logging.Logger:
    logger = logging.getLogger(name)
    if name not in _CONFIGURED:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(
            logging.Formatter("%(asctime)s  %(message)s", datefmt="%H:%M:%S")
        )
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
        logger.propagate = False  # don't double-print through uvicorn's root
        _CONFIGURED.add(name)
    return logger
