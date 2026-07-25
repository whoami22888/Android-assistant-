from __future__ import annotations

import os
from dataclasses import dataclass


def _bool(name: str, default: bool = False) -> bool:
    return os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    database_path: str = os.getenv("DATABASE_PATH", "/data/agent.db")
    workspace_path: str = os.getenv("WORKSPACE_PATH", "/workspace")
    pairing_code: str = os.getenv("PAIRING_CODE", "")
    token_secret: str = os.getenv("TOKEN_SECRET", "")
    google_api_key: str = os.getenv("GOOGLE_API_KEY", "")
    groq_api_key: str = os.getenv("GROQ_API_KEY", "")
    openrouter_api_key: str = os.getenv("OPENROUTER_API_KEY", "")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    groq_model: str = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")
    openrouter_model: str = os.getenv("OPENROUTER_MODEL", "meta-llama/llama-3.3-70b-instruct:free")
    allow_private_browser_targets: bool = _bool("ALLOW_PRIVATE_BROWSER_TARGETS")
    vision_enabled: bool = _bool("VISION_ENABLED")
    cors_origins: str = os.getenv("CORS_ORIGINS", "")
    max_agent_steps: int = int(os.getenv("MAX_AGENT_STEPS", "4"))
    context_budget_chars: int = int(os.getenv("CONTEXT_BUDGET_CHARS", "48000"))
    token_ttl_seconds: int = int(os.getenv("TOKEN_TTL_SECONDS", "2592000"))

    @property
    def configured(self) -> bool:
        return bool(self.pairing_code and self.token_secret)


settings = Settings()
