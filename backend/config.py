"""Central configuration for the Kavach agentic backend.

Everything reads from the project-root .env so nothing is hard-coded in the
agents themselves. Model IDs are the ones verified live against the key.
"""
from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / ".env")

# --- Gemini -----------------------------------------------------------------
GEMINI_API_KEY = os.getenv("GEMINI_AI_KEY", "").strip()

# Model IDs (verified available on this key, 2026-07).
MODEL_ORCHESTRATOR = os.getenv("KAVACH_MODEL_ORCHESTRATOR", "gemini-3.5-flash")
MODEL_VERIFICATION = os.getenv("KAVACH_MODEL_VERIFICATION", "gemini-3.5-flash")
MODEL_COMPUTER_USE = os.getenv(
    "KAVACH_MODEL_COMPUTER_USE", "gemini-2.5-computer-use-preview-10-2025"
)

# --- Twilio (Comms agent) ---------------------------------------------------
TWILIO_ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID", "").strip()
TWILIO_AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN", "").strip()
TWILIO_FROM_NUMBER = os.getenv("TWILIO_FROM_NUMBER", "").strip()
# Comma-separated verified emergency contact numbers, e.g. "+9198...,+9199..."
EMERGENCY_CONTACTS = [
    n.strip() for n in os.getenv("EMERGENCY_CONTACTS", "").split(",") if n.strip()
]
EMERGENCY_CONTACT_NAME = os.getenv("EMERGENCY_CONTACT_NAME", "your emergency contact")

TWILIO_ENABLED = bool(TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN and TWILIO_FROM_NUMBER)

# --- Demo behaviour ---------------------------------------------------------
# Run the Computer Use browser headless (True) or visibly on screen for the demo.
BROWSER_HEADLESS = os.getenv("KAVACH_HEADLESS", "false").lower() == "true"
# Default persona name used in outbound messages.
USER_NAME = os.getenv("KAVACH_USER_NAME", "Abhishek")
# Safeword that triggers Code Red.
SAFEWORD = os.getenv("KAVACH_SAFEWORD", "my battery is at 2 percent")

# Fallback location if the device sends none (central Bengaluru).
DEFAULT_LAT = float(os.getenv("KAVACH_DEFAULT_LAT", "12.9716"))
DEFAULT_LNG = float(os.getenv("KAVACH_DEFAULT_LNG", "77.5946"))


def require_api_key() -> str:
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_AI_KEY missing from .env")
    return GEMINI_API_KEY
