"""On-device LLM fallback — local Gemma via LM Studio.

When the phone loses connectivity (Wi-Fi off / no signal), the cloud agents
that depend on Gemini cannot run. This module gives Kavach a *real* offline
brain: a local Gemma model served by LM Studio's OpenAI-compatible endpoint
(default http://localhost:1234/v1).

Nothing here ever touches the network beyond localhost, so it keeps working
with the radio off — that is the whole point of DARK SURVIVAL mode.

Two things live here:
  - internet_up():  a fast reachability probe so the orchestrator can decide
                    whether to run the cloud agents or the on-device path.
  - chat():         a single-shot chat completion against the local Gemma model
                    (model id auto-detected from LM Studio, override via env).
"""
from __future__ import annotations

import asyncio
import socket
import time

import httpx

from . import config
from .logutil import get_logger

log = get_logger("kavach.gemma")

# Cache the resolved model id so we only hit /v1/models once per process.
_resolved_model: str | None = None


async def internet_up(timeout: float = None) -> bool:
    """True if the public internet (Gemini's host) is reachable.

    A plain TCP connect to generativelanguage.googleapis.com:443 — no bytes
    sent, no API call, just "is the radio actually carrying packets?". This is
    what flips the demo into DARK SURVIVAL the instant Wi-Fi is turned off.
    """
    timeout = timeout if timeout is not None else config.CONNECTIVITY_TIMEOUT_S

    def _probe() -> bool:
        try:
            with socket.create_connection(
                ("generativelanguage.googleapis.com", 443), timeout=timeout
            ):
                return True
        except OSError:
            return False

    try:
        up = await asyncio.wait_for(asyncio.to_thread(_probe), timeout=timeout + 1)
    except Exception:  # noqa: BLE001 — any failure means "treat as offline"
        up = False
    log.info(
        "🌐 connectivity probe → %s",
        "ONLINE (cloud reachable)" if up else "OFFLINE — engaging DARK SURVIVAL",
    )
    return up


async def is_up(timeout: float = 3.0) -> bool:
    """True if the local LM Studio server is reachable and has a model loaded."""
    if not config.LOCAL_LLM_ENABLED:
        return False
    try:
        return bool(await _detect_model(timeout=timeout))
    except Exception:  # noqa: BLE001
        return False


async def _detect_model(timeout: float = 3.0) -> str:
    """Resolve which local model to call.

    Honors KAVACH_LOCAL_MODEL if set; otherwise asks LM Studio which models are
    loaded and prefers the first Gemma. Cached after the first success.
    """
    global _resolved_model
    if config.LOCAL_LLM_MODEL:
        return config.LOCAL_LLM_MODEL
    if _resolved_model:
        return _resolved_model

    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.get(f"{config.LOCAL_LLM_BASE_URL}/models")
        resp.raise_for_status()
        models = [m.get("id", "") for m in resp.json().get("data", [])]

    if not models:
        raise RuntimeError("LM Studio has no model loaded")
    gemma = next((m for m in models if "gemma" in m.lower()), None)
    _resolved_model = gemma or models[0]
    log.info(
        "🧠 LM Studio @ %s — model detected: %s (loaded: %s)",
        config.LOCAL_LLM_BASE_URL, _resolved_model, ", ".join(models),
    )
    return _resolved_model


async def chat(
    prompt: str,
    system: str | None = None,
    timeout: float = None,
    temperature: float = 0.3,
    max_tokens: int = 1200,
) -> str:
    """Single-shot chat completion against the local Gemma model.

    Raises on any failure (server down, no model, HTTP error) so callers can
    fall back to their deterministic path — the demo never hard-fails.

    NOTE: gemma-4-e4b is a *reasoning* model — it spends tokens on hidden
    reasoning before the visible answer, so ``max_tokens`` must be generous or
    the reply truncates mid-thought (empty ``content``). If the server only
    returns ``reasoning_content`` (answer got cut), we surface that as a
    last resort rather than an empty string.
    """
    timeout = timeout if timeout is not None else config.LOCAL_LLM_TIMEOUT_S
    model = await _detect_model(timeout=min(timeout, 5.0))

    messages: list[dict] = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})

    payload = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": False,
    }

    preview = " ".join(prompt.split())[:90]
    log.info("🧠 [GEMMA] → %s | max_tokens=%s | prompt: %s…",
             model, max_tokens, preview)

    t0 = time.monotonic()
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.post(
            f"{config.LOCAL_LLM_BASE_URL}/chat/completions", json=payload
        )
        resp.raise_for_status()
        data = resp.json()
    dt = time.monotonic() - t0

    msg = data["choices"][0]["message"]
    usage = data.get("usage", {}) or {}
    content = (msg.get("content") or "").strip()
    truncated = not content and bool(msg.get("reasoning_content"))
    if truncated:
        # Answer was cut off inside the reasoning phase — surface it anyway.
        content = (msg.get("reasoning_content") or "").strip()

    log.info(
        "🧠 [GEMMA] ← %.1fs | tokens p=%s/c=%s (reasoning=%s)%s | reply: %s…",
        dt,
        usage.get("prompt_tokens", "?"),
        usage.get("completion_tokens", "?"),
        (usage.get("completion_tokens_details") or {}).get("reasoning_tokens", "?"),
        " [TRUNCATED→reasoning]" if truncated else "",
        " ".join(content.split())[:90],
    )
    return content


def resolved_model_name() -> str:
    """Best-effort human label for the active local model (for UI messages)."""
    return config.LOCAL_LLM_MODEL or _resolved_model or "Gemma (on-device)"
