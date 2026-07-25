from __future__ import annotations

import json
from typing import Any

import httpx

from .config import Settings


class ModelRouter:
    """Small provider router with deterministic fallbacks and no key persistence."""

    def __init__(self, config: Settings) -> None:
        self.config = config
        self.http = httpx.AsyncClient(timeout=httpx.Timeout(45.0, connect=10.0))

    async def close(self) -> None:
        await self.http.aclose()

    async def decide(self, messages: list[dict[str, str]], visual: bool = False) -> dict[str, Any]:
        providers = self._provider_order(visual)
        failures: list[str] = []
        for provider in providers:
            try:
                response = await getattr(self, f"_{provider}")(messages)
                return self._parse_json(response)
            except Exception as exc:  # Provider failures should not end a user task without fallback.
                failures.append(f"{provider}: {type(exc).__name__}")
        return {
            "kind": "reply",
            "message": "No configured model provider was available. Add one API key to .env and retry.",
            "provider_failures": failures,
        }

    def _provider_order(self, visual: bool) -> list[str]:
        if visual:
            return [name for name, key in [("gemini", self.config.google_api_key)] if key]
        ordered = [
            ("groq", self.config.groq_api_key),
            ("gemini", self.config.google_api_key),
            ("openrouter", self.config.openrouter_api_key),
        ]
        return [name for name, key in ordered if key]

    @staticmethod
    def _parse_json(content: str) -> dict[str, Any]:
        content = content.strip()
        if content.startswith("```"):
            content = content.split("\n", 1)[-1].rsplit("```", 1)[0].strip()
        parsed = json.loads(content)
        if not isinstance(parsed, dict):
            raise ValueError("Model output was not a JSON object")
        return parsed

    async def _groq(self, messages: list[dict[str, str]]) -> str:
        result = await self.http.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={"Authorization": f"Bearer {self.config.groq_api_key}"},
            json={"model": self.config.groq_model, "messages": messages, "temperature": 0.1, "response_format": {"type": "json_object"}},
        )
        result.raise_for_status()
        return result.json()["choices"][0]["message"]["content"]

    async def _openrouter(self, messages: list[dict[str, str]]) -> str:
        result = await self.http.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={"Authorization": f"Bearer {self.config.openrouter_api_key}", "HTTP-Referer": "https://localhost"},
            json={"model": self.config.openrouter_model, "messages": messages, "temperature": 0.1, "response_format": {"type": "json_object"}},
        )
        result.raise_for_status()
        return result.json()["choices"][0]["message"]["content"]

    async def _gemini(self, messages: list[dict[str, str]]) -> str:
        system = "\n".join(message["content"] for message in messages if message["role"] == "system")
        turns = [
            {"role": "model" if message["role"] == "assistant" else "user", "parts": [{"text": message["content"]}]}
            for message in messages
            if message["role"] != "system"
        ]
        result = await self.http.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{self.config.gemini_model}:generateContent?key={self.config.google_api_key}",
            json={
                "system_instruction": {"parts": [{"text": system}]},
                "contents": turns,
                "generationConfig": {"temperature": 0.1, "responseMimeType": "application/json"},
            },
        )
        result.raise_for_status()
        return result.json()["candidates"][0]["content"]["parts"][0]["text"]
