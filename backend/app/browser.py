from __future__ import annotations

import ipaddress
import socket
from urllib.parse import urlparse

from playwright.async_api import async_playwright

from .config import Settings


class BrowserTool:
    def __init__(self, config: Settings) -> None:
        self.config = config

    async def extract(self, url: str) -> dict[str, str]:
        await self._validate_url(url)
        async with async_playwright() as playwright:
            browser = await playwright.chromium.launch(headless=True)
            try:
                page = await browser.new_page(viewport={"width": 1280, "height": 900})
                await page.goto(url, wait_until="domcontentloaded", timeout=25_000)
                title = await page.title()
                text = await page.locator("body").inner_text(timeout=10_000)
                return {"title": title[:300], "text": text[:12000]}
            finally:
                await browser.close()

    async def _validate_url(self, value: str) -> None:
        parsed = urlparse(value)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("Only absolute HTTP(S) URLs are accepted")
        if self.config.allow_private_browser_targets:
            return
        addresses = {entry[4][0] for entry in socket.getaddrinfo(parsed.hostname, None)}
        for address in addresses:
            ip = ipaddress.ip_address(address)
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast:
                raise ValueError("Private and reserved browser targets are blocked")
