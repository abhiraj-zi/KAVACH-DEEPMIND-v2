"""Action Agent — Zero-UI app manipulation via Gemini Computer Use.

Drives a real Chromium browser (Playwright) with the
`gemini-2.5-computer-use-preview` model to open Google Maps, resolve the
user's location, and route to the nearest police station / 24-hour safe zone —
with no human touching the screen.

Every model action is streamed to the UI as a "Computer Use" event so judges
watch the agent think and click in real time. The loop is bounded by a step
cap and wall-clock timeout, and any failure degrades to a deterministic result
(still a real, openable Maps link) so the live demo never dead-ends.
"""
from __future__ import annotations

import asyncio
import re
from dataclasses import dataclass, field
from typing import Optional

from google import genai
from google.genai import types

from .. import config
from ..events import AGENT_ACTION, EventBus

# Bounds that keep the live demo snappy and safe.
MAX_STEPS = 14
STEP_TIMEOUT_S = 120
VIEWPORT = {"width": 1280, "height": 800}

# The predefined Computer Use browser actions we translate into Playwright.
_KEY_MAP = {"enter": "Enter", "return": "Enter", "tab": "Tab", "escape": "Escape"}


@dataclass
class ActionResult:
    ok: bool
    location_text: str
    maps_url: str
    safe_zone: str
    summary: str
    eta: Optional[str] = None
    steps: list[str] = field(default_factory=list)


def _maps_search_url(lat: float, lng: float, query: str = "police station") -> str:
    q = query.replace(" ", "+")
    return f"https://www.google.com/maps/search/{q}/@{lat},{lng},15z"


def _parse_safe_zone(
    text: str, fallback: str = "Nearest Police Station"
) -> tuple[str, Optional[str]]:
    """Pull the resolved police-station name + ETA out of the model's closing text."""
    if not text:
        return fallback, None

    # ETA: "ETA 5 mins", "5 minute", "about 7 min"
    eta: Optional[str] = None
    m = re.search(r"\b(\d+)\s*(min(?:ute)?s?|hours?|hrs?)\b", text, re.I)
    if m:
        eta = f"{m.group(1)} {m.group(2)}"

    # Name: the first "... Police Station" (or "... Thana"/"Chowki") phrase.
    name = fallback
    m2 = re.search(
        r"([A-Z][A-Za-z0-9.&'\-]*(?:\s+[A-Z][A-Za-z0-9.&'\-]*){0,4}"
        r"\s+(?:Police\s+Station|Thana|Chowki))",
        text,
    )
    if m2:
        name = re.sub(r"\s+", " ", m2.group(1)).strip()
    return name, eta


async def run_action_agent(
    bus: EventBus,
    lat: Optional[float] = None,
    lng: Optional[float] = None,
) -> ActionResult:
    lat = lat if lat is not None else config.DEFAULT_LAT
    lng = lng if lng is not None else config.DEFAULT_LNG
    location_text = f"{lat:.4f}, {lng:.4f}"
    fallback_url = _maps_search_url(lat, lng)

    await bus.emit("Action", AGENT_ACTION,
                   "Action Agent spawned — acquiring GPS lock (zero-UI).")

    try:
        return await asyncio.wait_for(
            _drive_browser(bus, lat, lng, location_text, fallback_url),
            timeout=STEP_TIMEOUT_S,
        )
    except Exception as exc:  # noqa: BLE001 — demo must survive any browser error
        await bus.emit(
            "Action", AGENT_ACTION,
            f"Computer Use interrupted ({type(exc).__name__}); using resolved "
            f"route to nearest police station.",
        )
        return ActionResult(
            ok=False,
            location_text=location_text,
            maps_url=fallback_url,
            safe_zone="Nearest Police Station",
            summary="Fallback route resolved to nearest police station.",
        )


async def _drive_browser(
    bus: EventBus,
    lat: float,
    lng: float,
    location_text: str,
    fallback_url: str,
) -> ActionResult:
    from playwright.async_api import async_playwright

    client = genai.Client(api_key=config.require_api_key())
    tool = types.Tool(
        computer_use=types.ComputerUse(
            environment=types.Environment.ENVIRONMENT_BROWSER
        )
    )
    gen_config = types.GenerateContentConfig(tools=[tool])

    goal = (
        "You are operating a web browser to help a person in danger. "
        f"The user's current GPS location is latitude {lat}, longitude {lng}. "
        "Open Google Maps, search for the nearest POLICE STATION to that "
        "location, open its listing, and get walking/driving directions to it. "
        "Work quickly and stop once directions to the nearest police station "
        "are shown. Then briefly state the police station name and ETA."
    )

    steps: list[str] = []
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=config.BROWSER_HEADLESS)
        context = await browser.new_context(
            viewport=VIEWPORT,
            geolocation={"latitude": lat, "longitude": lng},
            permissions=["geolocation"],
            locale="en-US",
        )
        page = await context.new_page()
        await page.goto(fallback_url, wait_until="domcontentloaded")
        await asyncio.sleep(2)

        async def screenshot_part() -> types.Part:
            png = await page.screenshot(type="png")
            return types.Part(
                inline_data=types.Blob(mime_type="image/png", data=png)
            )

        contents: list[types.Content] = [
            types.Content(
                role="user",
                parts=[types.Part(text=goal), await screenshot_part()],
            )
        ]

        final_text = ""
        safe_zone = "Nearest Police Station"
        for step in range(MAX_STEPS):
            resp = await client.aio.models.generate_content(
                model=config.MODEL_COMPUTER_USE,
                contents=contents,
                config=gen_config,
            )
            if not resp.candidates:
                break
            candidate = resp.candidates[0]
            contents.append(candidate.content)

            calls = [
                p.function_call
                for p in (candidate.content.parts or [])
                if p.function_call is not None
            ]
            texts = [
                p.text for p in (candidate.content.parts or []) if p.text
            ]
            if texts:
                final_text = " ".join(texts).strip()

            if not calls:
                # Model produced only text -> it's done.
                if final_text:
                    await bus.emit("Action", AGENT_ACTION, final_text)
                break

            # Execute each requested UI action and return a fresh screenshot.
            response_parts: list[types.Part] = []
            for fc in calls:
                label = await _execute(page, fc, bus)
                steps.append(label)
                response_parts.append(
                    types.Part(
                        function_response=types.FunctionResponse(
                            name=fc.name,
                            response={"url": page.url},
                            parts=[
                                types.FunctionResponsePart(
                                    inline_data=types.FunctionResponseBlob(
                                        mime_type="image/png",
                                        data=await page.screenshot(type="png"),
                                    )
                                )
                            ],
                        )
                    )
                )
            contents.append(types.Content(role="user", parts=response_parts))

        maps_url = page.url or fallback_url
        safe_zone, eta = _parse_safe_zone(final_text, fallback=safe_zone)
        eta_txt = f" · ETA {eta}" if eta else ""
        await bus.emit(
            "Action", AGENT_ACTION,
            f"Route locked to {safe_zone}{eta_txt}. Live map ready.",
            status="active",
        )
        await context.close()
        await browser.close()

    return ActionResult(
        ok=True,
        location_text=location_text,
        maps_url=maps_url if maps_url.startswith("http") else fallback_url,
        safe_zone=safe_zone,
        summary=final_text or "Directions to nearest police station acquired.",
        eta=eta,
        steps=steps,
    )


async def _execute(page, fc, bus: EventBus) -> str:
    """Translate one Gemini Computer Use action into a Playwright action."""
    name = fc.name
    args = dict(fc.args or {})
    w, h = VIEWPORT["width"], VIEWPORT["height"]

    def px(nx, ny):
        return (nx / 1000.0) * w, (ny / 1000.0) * h

    try:
        if name == "open_web_browser":
            label = "Opening browser"
        elif name == "navigate":
            url = args.get("url", "")
            await page.goto(url, wait_until="domcontentloaded")
            label = f"Navigating to {url[:40]}"
        elif name in ("go_back",):
            await page.go_back()
            label = "Going back"
        elif name in ("go_forward",):
            await page.go_forward()
            label = "Going forward"
        elif name == "search":
            label = "Searching"
        elif name == "click_at":
            x, y = px(args.get("x", 0), args.get("y", 0))
            await page.mouse.click(x, y)
            label = f"Clicking at ({int(x)},{int(y)})"
        elif name == "hover_at":
            x, y = px(args.get("x", 0), args.get("y", 0))
            await page.mouse.move(x, y)
            label = "Hovering"
        elif name == "type_text_at":
            x, y = px(args.get("x", 0), args.get("y", 0))
            await page.mouse.click(x, y)
            if args.get("clear_before_typing", True):
                await page.keyboard.press("Control+A")
                await page.keyboard.press("Delete")
            text = args.get("text", "")
            await page.keyboard.type(text, delay=20)
            if args.get("press_enter"):
                await page.keyboard.press("Enter")
            label = f"Typing '{text[:30]}'"
        elif name == "key_combination":
            keys = args.get("keys", "")
            combo = "+".join(_KEY_MAP.get(k.lower(), k) for k in keys.split("+"))
            await page.keyboard.press(combo)
            label = f"Pressing {combo}"
        elif name == "scroll_document":
            direction = args.get("direction", "down")
            dy = 600 if direction == "down" else -600
            await page.mouse.wheel(0, dy)
            label = f"Scrolling {direction}"
        elif name == "scroll_at":
            x, y = px(args.get("x", 500), args.get("y", 500))
            direction = args.get("direction", "down")
            mag = args.get("magnitude", 600)
            await page.mouse.move(x, y)
            await page.mouse.wheel(0, mag if direction == "down" else -mag)
            label = f"Scrolling {direction}"
        elif name in ("wait_5_seconds", "wait"):
            await asyncio.sleep(2)
            label = "Waiting for page"
        else:
            label = f"Action: {name}"
        await asyncio.sleep(0.8)  # let the UI settle before the next screenshot
    except Exception as exc:  # noqa: BLE001
        label = f"{name} failed ({type(exc).__name__})"

    await bus.emit("Action", AGENT_ACTION, label)
    return label
