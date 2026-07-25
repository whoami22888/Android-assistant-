#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
mkdir -p "$ROOT/backend/app" "$ROOT/backend/tests" "$ROOT/docs"

cat > "$ROOT/.env.example" <<'EOF'
# Copy to .env and replace all values before exposing the gateway.
PAIRING_CODE=replace-with-a-long-one-time-pairing-code
TOKEN_SECRET=replace-with-a-32-byte-or-longer-random-secret
GOOGLE_API_KEY=
GROQ_API_KEY=
OPENROUTER_API_KEY=
GEMINI_MODEL=gemini-2.5-flash
GROQ_MODEL=llama-3.3-70b-versatile
OPENROUTER_MODEL=meta-llama/llama-3.3-70b-instruct:free
ALLOW_PRIVATE_BROWSER_TARGETS=false
VISION_ENABLED=false
CORS_ORIGINS=
LOG_LEVEL=INFO
EOF

cat > "$ROOT/Caddyfile.example" <<'EOF'
# Replace agent.example.com with a DNS name that resolves to this host.
# Caddy obtains and renews a public TLS certificate when ports 80 and 443 are reachable.
agent.example.com {
    encode zstd gzip
    reverse_proxy agent-api:8080
}
EOF

cat > "$ROOT/docker-compose.yml" <<'EOF'
services:
  agent-api:
    build:
      context: ./backend
      dockerfile: Dockerfile
    env_file:
      - .env
    ports:
      - "${AGENT_PORT:-8080}:8080"
    volumes:
      - agent_data:/data
      - agent_workspace:/workspace
    read_only: true
    tmpfs:
      - /tmp:rw,nosuid,nodev,size=128m,mode=1777
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    shm_size: 512mb
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=3)"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 20s
    deploy:
      resources:
        limits:
          cpus: "1.50"
          memory: 1536M

  # Optional TLS edge. Copy Caddyfile.example to Caddyfile and set a real hostname before enabling.
  caddy:
    image: caddy:2.8-alpine
    profiles: ["tls"]
    depends_on:
      agent-api:
        condition: service_healthy
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    restart: unless-stopped

  # Optional, on-demand Android compiler. It never starts with `up`.
  android-build:
    profiles: ["android-build"]
    build:
      context: ./backend
      dockerfile: Dockerfile.android-builder
    working_dir: /workspace/android-agent
    volumes:
      - ./:/workspace:rw
      - gradle_cache:/home/gradle/.gradle
    entrypoint: ["gradle"]

volumes:
  agent_data:
  agent_workspace:
  gradle_cache:
  caddy_data:
  caddy_config:
EOF

cat > "$ROOT/backend/Dockerfile" <<'EOF'
FROM mcr.microsoft.com/playwright/python:v1.52.0-noble

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HOME=/tmp

RUN useradd --create-home --uid 10001 --shell /usr/sbin/nologin agent \
    && mkdir -p /app /data /workspace \
    && chown -R agent:agent /app /data /workspace

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
RUN chown -R agent:agent /app

USER agent
EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080", "--proxy-headers"]
EOF

cat > "$ROOT/backend/Dockerfile.android-builder" <<'EOF'
FROM gradle:8.10.2-jdk17

USER root
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget unzip \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/tools.zip \
    && unzip -q /tmp/tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/tools.zip \
    && yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null \
    && ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
         "platform-tools" \
         "platforms;android-35" \
         "build-tools;35.0.0"
USER gradle
EOF

cat > "$ROOT/backend/requirements.txt" <<'EOF'
fastapi==0.115.12
uvicorn[standard]==0.34.2
httpx==0.28.1
pydantic==2.11.3
playwright==1.52.0
EOF

cat > "$ROOT/backend/app/__init__.py" <<'EOF'
"""Consent-first thin-client agent gateway."""
EOF

cat > "$ROOT/backend/app/config.py" <<'EOF'
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
EOF

cat > "$ROOT/backend/app/schemas.py" <<'EOF'
from __future__ import annotations

from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field, HttpUrl, field_validator


class RiskLevel(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class PairRequest(BaseModel):
    device_id: str = Field(min_length=8, max_length=128, pattern=r"^[A-Za-z0-9._-]+$")
    device_name: str = Field(min_length=1, max_length=80)
    pairing_code: str = Field(min_length=12, max_length=256)


class PairResponse(BaseModel):
    access_token: str
    expires_at: int
    gateway_protocol: Literal["agent-gateway/v1"] = "agent-gateway/v1"


class TaskRequest(BaseModel):
    task_id: str | None = Field(default=None, max_length=80)
    prompt: str = Field(min_length=1, max_length=12000)
    depth: int = Field(default=0, ge=0, le=4)


class VisionRequest(BaseModel):
    image_base64: str = Field(min_length=16, max_length=1_500_000)
    instruction: str = Field(default="Describe visible actionable UI elements only.", max_length=600)


class DeviceAction(BaseModel):
    """An action proposal only; Android executes it only after local policy evaluation."""

    id: str = Field(min_length=8, max_length=80)
    kind: Literal[
        "click_text",
        "click_view_id",
        "scroll_forward",
        "scroll_backward",
        "set_text",
        "long_click_text",
        "tap_coordinate",
        "launch_app",
        "open_settings",
    ]
    risk: RiskLevel = RiskLevel.MEDIUM
    reason: str = Field(min_length=1, max_length=500)
    text: str | None = Field(default=None, max_length=1000)
    view_id: str | None = Field(default=None, max_length=300)
    package_name: str | None = Field(default=None, max_length=240)
    x: float | None = Field(default=None, ge=0, le=10000)
    y: float | None = Field(default=None, ge=0, le=10000)
    timeout_ms: int = Field(default=3500, ge=200, le=10000)

    @field_validator("text", "view_id", "package_name")
    @classmethod
    def strip_optional(cls, value: str | None) -> str | None:
        return value.strip() if value else None


class DeviceResult(BaseModel):
    action_id: str = Field(min_length=8, max_length=80)
    status: Literal["approved", "rejected", "executed", "failed"]
    detail: str = Field(default="", max_length=1000)


class AgentDecision(BaseModel):
    kind: Literal["reply", "browser_extract", "sandbox_python", "device_action"]
    message: str = Field(default="", max_length=6000)
    url: str | None = None
    code: str | None = Field(default=None, max_length=8000)
    action: DeviceAction | None = None


class AgentResult(BaseModel):
    task_id: str
    state: Literal["completed", "awaiting_approval", "failed"]
    message: str
    events: list[dict[str, Any]] = Field(default_factory=list)
    action: DeviceAction | None = None


class BrowserRequest(BaseModel):
    url: HttpUrl
EOF

cat > "$ROOT/backend/app/security.py" <<'EOF'
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass

from fastapi import Header, HTTPException, status

from .config import settings


def _b64_encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _b64_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def issue_device_token(device_id: str) -> tuple[str, int]:
    if not settings.token_secret:
        raise RuntimeError("TOKEN_SECRET is not configured")
    expiry = int(time.time()) + settings.token_ttl_seconds
    payload = {"sub": device_id, "exp": expiry, "aud": "agent-gateway/v1"}
    encoded = _b64_encode(json.dumps(payload, separators=(",", ":")).encode())
    signature = hmac.new(settings.token_secret.encode(), encoded.encode(), hashlib.sha256).digest()
    return f"{encoded}.{_b64_encode(signature)}", expiry


def verify_device_token(token: str) -> str:
    try:
        encoded, supplied_signature = token.split(".", 1)
        expected_signature = hmac.new(settings.token_secret.encode(), encoded.encode(), hashlib.sha256).digest()
        if not hmac.compare_digest(_b64_encode(expected_signature), supplied_signature):
            raise ValueError("invalid signature")
        payload = json.loads(_b64_decode(encoded))
        if payload.get("aud") != "agent-gateway/v1" or int(payload["exp"]) < int(time.time()):
            raise ValueError("expired or invalid audience")
        return str(payload["sub"])
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid device token") from exc


async def authenticated_device(x_agent_token: str = Header(default="")) -> str:
    return verify_device_token(x_agent_token)


def verify_pairing_code(code: str) -> bool:
    return bool(settings.pairing_code) and hmac.compare_digest(settings.pairing_code, code)
EOF

cat > "$ROOT/backend/app/database.py" <<'EOF'
from __future__ import annotations

import json
import sqlite3
import threading
import time
from pathlib import Path
from typing import Any


class Database:
    def __init__(self, path: str) -> None:
        self.path = path
        self._lock = threading.RLock()

    def initialize(self) -> None:
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.executescript(
                """
                PRAGMA journal_mode=WAL;
                PRAGMA foreign_keys=ON;
                CREATE TABLE IF NOT EXISTS devices (
                    device_id TEXT PRIMARY KEY,
                    device_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_seen_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS tasks (
                    task_id TEXT PRIMARY KEY,
                    device_id TEXT NOT NULL,
                    state TEXT NOT NULL,
                    summary TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS events_by_task ON events(task_id, id);
                """
            )

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path, check_same_thread=False)
        connection.row_factory = sqlite3.Row
        return connection

    def upsert_device(self, device_id: str, device_name: str) -> None:
        now = int(time.time())
        with self._lock, self._connect() as connection:
            connection.execute(
                """
                INSERT INTO devices(device_id, device_name, created_at, last_seen_at)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET device_name=excluded.device_name, last_seen_at=excluded.last_seen_at
                """,
                (device_id, device_name, now, now),
            )

    def create_task(self, task_id: str, device_id: str) -> None:
        now = int(time.time())
        with self._lock, self._connect() as connection:
            connection.execute(
                "INSERT INTO tasks(task_id, device_id, state, created_at, updated_at) VALUES(?, ?, 'running', ?, ?)",
                (task_id, device_id, now, now),
            )

    def set_task_state(self, task_id: str, state: str, summary: str = "") -> None:
        with self._lock, self._connect() as connection:
            connection.execute(
                "UPDATE tasks SET state=?, summary=?, updated_at=? WHERE task_id=?",
                (state, summary[:6000], int(time.time()), task_id),
            )

    def append_event(self, task_id: str, kind: str, payload: dict[str, Any]) -> None:
        with self._lock, self._connect() as connection:
            connection.execute(
                "INSERT INTO events(task_id, kind, payload, created_at) VALUES(?, ?, ?, ?)",
                (task_id, kind, json.dumps(payload, separators=(",", ":")), int(time.time())),
            )

    def recent_events(self, task_id: str, limit: int = 30) -> list[dict[str, Any]]:
        with self._lock, self._connect() as connection:
            rows = connection.execute(
                "SELECT kind, payload, created_at FROM events WHERE task_id=? ORDER BY id DESC LIMIT ?", (task_id, limit)
            ).fetchall()
        return [
            {"kind": row["kind"], "payload": json.loads(row["payload"]), "created_at": row["created_at"]}
            for row in reversed(rows)
        ]
EOF

cat > "$ROOT/backend/app/providers.py" <<'EOF'
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
EOF

cat > "$ROOT/backend/app/browser.py" <<'EOF'
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
EOF

cat > "$ROOT/backend/app/sandbox.py" <<'EOF'
from __future__ import annotations

import ast
import os
import resource
import subprocess
import tempfile
from pathlib import Path


SAFE_IMPORTS = {"math", "statistics", "json", "re", "datetime", "collections", "itertools", "numpy", "pandas", "plotly"}
FORBIDDEN_NAMES = {"open", "exec", "eval", "compile", "__import__", "input", "globals", "locals", "vars", "getattr", "setattr", "delattr", "breakpoint"}


class RestrictedPython(ast.NodeVisitor):
    def visit_Import(self, node: ast.Import) -> None:
        for alias in node.names:
            if alias.name.split(".")[0] not in SAFE_IMPORTS:
                raise ValueError(f"Import is not allowed: {alias.name}")

    def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
        if not node.module or node.module.split(".")[0] not in SAFE_IMPORTS:
            raise ValueError(f"Import is not allowed: {node.module}")

    def visit_Name(self, node: ast.Name) -> None:
        if node.id in FORBIDDEN_NAMES or node.id.startswith("__"):
            raise ValueError(f"Name is not allowed: {node.id}")

    def visit_Attribute(self, node: ast.Attribute) -> None:
        if node.attr.startswith("__"):
            raise ValueError("Dunder attribute access is not allowed")
        self.generic_visit(node)


class PythonSandbox:
    """A limited analysis runner, not a general-purpose shell. Compose hardening is a second boundary."""

    def __init__(self, workspace: str) -> None:
        self.workspace = Path(workspace)
        self.workspace.mkdir(parents=True, exist_ok=True)

    def run(self, code: str) -> dict[str, str | int]:
        if len(code) > 8000:
            raise ValueError("Analysis script exceeds 8,000 characters")
        tree = ast.parse(code, mode="exec")
        RestrictedPython().visit(tree)
        with tempfile.TemporaryDirectory(dir=self.workspace) as directory:
            script = Path(directory) / "analysis.py"
            script.write_text(code, encoding="utf-8")
            result = subprocess.run(
                ["python", "-I", str(script)],
                cwd=directory,
                env={"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C.UTF-8", "PYTHONNOUSERSITE": "1"},
                text=True,
                capture_output=True,
                timeout=12,
                preexec_fn=self._limits,
                check=False,
            )
            return {"exit_code": result.returncode, "stdout": result.stdout[-12000:], "stderr": result.stderr[-4000:]}

    @staticmethod
    def _limits() -> None:
        resource.setrlimit(resource.RLIMIT_CPU, (8, 8))
        resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024, 512 * 1024 * 1024))
        resource.setrlimit(resource.RLIMIT_FSIZE, (8 * 1024 * 1024, 8 * 1024 * 1024))
EOF

cat > "$ROOT/backend/app/agent.py" <<'EOF'
from __future__ import annotations

import json
import uuid
from typing import Any

from pydantic import ValidationError

from .browser import BrowserTool
from .config import Settings
from .database import Database
from .providers import ModelRouter
from .sandbox import PythonSandbox
from .schemas import AgentDecision, AgentResult, DeviceAction, RiskLevel, TaskRequest

SYSTEM_PROMPT = """You are a consent-first automation planner. Return one JSON object only, with no hidden reasoning.
You work in a bounded cycle: inspect the task and prior observations, select exactly one next action, observe its result, then either select another action or finish with reply.
Allowed kinds: reply, browser_extract, sandbox_python, device_action.
Never claim an action has run. device_action is only a proposal for a locally supervised Android device.
Never request or process passwords, payment data, authentication codes, identity data, destructive file operations, or system-security changes.
For device_action, use a specific reason, action id, risk, and only one benign action.
Browser extraction is read-only; do not submit forms. Sandbox Python is for offline data analysis only.
When prior observations adequately answer the task, select reply with a concise user-facing answer.
Schema examples:
{"kind":"reply","message":"..."}
{"kind":"browser_extract","url":"https://example.com","message":"..."}
{"kind":"sandbox_python","code":"print(2 + 2)","message":"..."}
{"kind":"device_action","message":"...","action":{"id":"action-12345678","kind":"click_text","risk":"low","reason":"Select the user-requested tab","text":"Settings"}}
"""


class AgentOrchestrator:
    """A shallow ReAct executor. Tools produce observations; only the final answer or local approval leaves the loop."""

    def __init__(self, config: Settings, database: Database, router: ModelRouter) -> None:
        self.config = config
        self.database = database
        self.router = router
        self.browser = BrowserTool(config)
        self.sandbox = PythonSandbox(config.workspace_path)

    async def run(self, device_id: str, request: TaskRequest) -> AgentResult:
        if request.depth >= self.config.max_agent_steps:
            return AgentResult(task_id=request.task_id or "", state="failed", message="Maximum sub-task depth reached.")

        task_id = request.task_id or f"task-{uuid.uuid4().hex[:16]}"
        self.database.create_task(task_id, device_id)
        self.database.append_event(task_id, "user_request", {"prompt": request.prompt})
        last_observation = ""

        for step in range(1, self.config.max_agent_steps + 1):
            memory = self.database.recent_events(task_id)
            try:
                raw = await self.router.decide(self._messages(request.prompt, memory, step))
                decision = AgentDecision.model_validate(raw)
            except (ValidationError, ValueError) as exc:
                self.database.set_task_state(task_id, "failed", "Invalid model decision")
                return AgentResult(task_id=task_id, state="failed", message=f"Model returned an invalid action plan: {exc}")

            self.database.append_event(
                task_id,
                "agent_decision",
                {"step": step, **decision.model_dump(exclude_none=True)},
            )
            if decision.kind == "reply":
                return self._complete(task_id, decision.message or "Completed without a device action.")
            if decision.kind == "device_action":
                if not decision.action:
                    return self._fail(task_id, "Device action was missing its payload.")
                action = self._normalize_action(decision.action)
                self.database.set_task_state(task_id, "awaiting_approval", decision.message or action.reason)
                return AgentResult(task_id=task_id, state="awaiting_approval", message=decision.message or action.reason, action=action)

            observation = await self._execute_tool(task_id, decision)
            if observation is None:
                return self._fail(task_id, "Unsupported agent action.")
            if observation.startswith("ERROR:"):
                return self._fail(task_id, observation.removeprefix("ERROR: "))
            last_observation = observation

        final_message = "Execution stopped at the configured four-step limit."
        if last_observation:
            final_message += f" Last observation: {last_observation[:2000]}"
        return self._complete(task_id, final_message)

    async def _execute_tool(self, task_id: str, decision: AgentDecision) -> str | None:
        if decision.kind == "browser_extract":
            if not decision.url:
                return "ERROR: Browser request was missing its URL."
            try:
                page = await self.browser.extract(decision.url)
                self.database.append_event(task_id, "browser_observation", page)
                return f"Read-only browser observation: {page['title']}\n{page['text']}"
            except Exception as exc:
                return f"ERROR: Read-only browser extraction failed: {type(exc).__name__}: {exc}"

        if decision.kind == "sandbox_python":
            if not decision.code:
                return "ERROR: Sandbox request was missing Python code."
            try:
                result = self.sandbox.run(decision.code)
                self.database.append_event(task_id, "sandbox_observation", result)
                return f"Restricted analysis observation (exit {result['exit_code']}):\n{result['stdout']}\n{result['stderr']}"
            except Exception as exc:
                return f"ERROR: Restricted analysis failed: {type(exc).__name__}: {exc}"
        return None

    def record_device_result(self, task_id: str, payload: dict[str, Any]) -> None:
        self.database.append_event(task_id, "device_result", payload)

    def _messages(self, prompt: str, memory: list[dict[str, Any]], step: int) -> list[dict[str, str]]:
        packed_memory, compacted = self._pack_memory(memory)
        context_note = "Event memory was compacted deterministically to stay below the context budget." if compacted else ""
        return [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": f"Task: {prompt}\nStep: {step}/{self.config.max_agent_steps}\n{context_note}\nPrior events: {packed_memory}",
            },
        ]

    def _pack_memory(self, memory: list[dict[str, Any]]) -> tuple[str, bool]:
        packed = json.dumps(memory[-20:], separators=(",", ":"), ensure_ascii=False)
        threshold = int(self.config.context_budget_chars * 0.8)
        if len(packed) <= threshold:
            return packed, False
        summaries = []
        for event in memory[-20:]:
            payload = json.dumps(event.get("payload", {}), separators=(",", ":"), ensure_ascii=False)
            summaries.append(
                {
                    "kind": event.get("kind", "event"),
                    "created_at": event.get("created_at"),
                    "summary": payload[:700] + ("…" if len(payload) > 700 else ""),
                }
            )
        return json.dumps(summaries, separators=(",", ":"), ensure_ascii=False), True

    def _normalize_action(self, action: DeviceAction) -> DeviceAction:
        # Coordinates and text input can affect hidden or sensitive UI, so they always receive high-risk handling locally.
        if action.kind in {"tap_coordinate", "set_text", "launch_app", "open_settings"} and action.risk == RiskLevel.LOW:
            action.risk = RiskLevel.HIGH
        return action

    def _complete(self, task_id: str, message: str) -> AgentResult:
        self.database.set_task_state(task_id, "completed", message)
        self.database.append_event(task_id, "completed", {"message": message})
        return AgentResult(task_id=task_id, state="completed", message=message)

    def _fail(self, task_id: str, message: str) -> AgentResult:
        self.database.set_task_state(task_id, "failed", message)
        self.database.append_event(task_id, "failed", {"message": message})
        return AgentResult(task_id=task_id, state="failed", message=message)
EOF
cat > "$ROOT/backend/app/main.py" <<'EOF'
from __future__ import annotations

import asyncio
import base64
import json
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

from .agent import AgentOrchestrator
from .config import settings
from .database import Database
from .providers import ModelRouter
from .schemas import DeviceResult, PairRequest, PairResponse, TaskRequest, VisionRequest
from .security import authenticated_device, issue_device_token, verify_device_token, verify_pairing_code

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("agent-gateway")
database = Database(settings.database_path)
router = ModelRouter(settings)
orchestrator = AgentOrchestrator(settings, database, router)


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not settings.configured:
        logger.warning("Gateway starts unpaired: configure PAIRING_CODE and TOKEN_SECRET in .env")
    database.initialize()
    yield
    await router.close()


app = FastAPI(title="Consent-first Agent Gateway", version="1.0.0", lifespan=lifespan)
origins = [origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()]
if origins:
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False, allow_methods=["GET", "POST"], allow_headers=["*"])


@app.get("/health")
async def health() -> dict[str, object]:
    return {"ok": True, "configured": settings.configured, "protocol": "agent-gateway/v1"}


@app.post("/v1/pair", response_model=PairResponse)
async def pair(request: PairRequest) -> PairResponse:
    if not settings.configured:
        raise HTTPException(status_code=503, detail="Gateway pairing is not configured")
    if not verify_pairing_code(request.pairing_code):
        raise HTTPException(status_code=401, detail="Invalid pairing code")
    database.upsert_device(request.device_id, request.device_name)
    token, expiry = issue_device_token(request.device_id)
    return PairResponse(access_token=token, expires_at=expiry)


@app.post("/v1/tasks")
async def create_task(request: TaskRequest, device_id: str = Depends(authenticated_device)) -> dict[str, object]:
    result = await orchestrator.run(device_id, request)
    return result.model_dump(exclude_none=True)


@app.post("/v1/tasks/{task_id}/device-result")
async def device_result(task_id: str, payload: DeviceResult, device_id: str = Depends(authenticated_device)) -> dict[str, bool]:
    orchestrator.record_device_result(task_id, {"device_id": device_id, **payload.model_dump()})
    return {"ok": True}


@app.post("/v1/vision/analyze")
async def analyze_frame(request: VisionRequest, device_id: str = Depends(authenticated_device)) -> dict[str, str]:
    # Vision is intentionally opt-in and is never persisted. The current compact gateway uses the text router only.
    if not settings.vision_enabled or not settings.google_api_key:
        raise HTTPException(status_code=403, detail="Vision is disabled. Enable VISION_ENABLED and configure Google API access.")
    try:
        raw = base64.b64decode(request.image_base64, validate=True)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="Invalid image payload") from exc
    if len(raw) > 1_000_000:
        raise HTTPException(status_code=413, detail="Image frame exceeds the 1 MB privacy limit")
    # A full multimodal call is deliberately left behind the explicit feature flag; no frame is stored or logged.
    return {"message": "Frame accepted for explicit, non-persistent visual analysis. Configure a Gemini vision adapter before enabling in production."}


@app.websocket("/v1/ws")
async def websocket_gateway(websocket: WebSocket) -> None:
    token = websocket.query_params.get("token", "")
    try:
        device_id = verify_device_token(token)
    except HTTPException:
        await websocket.close(code=1008)
        return
    await websocket.accept(subprotocol="agent-gateway.v1")
    await websocket.send_json({"type": "connected", "device_id": device_id})
    try:
        while True:
            incoming = await websocket.receive_json()
            message_type = incoming.get("type")
            if message_type == "task":
                request = TaskRequest.model_validate(incoming.get("payload", {}))
                result = await orchestrator.run(device_id, request)
                if result.action:
                    await websocket.send_json({"type": "device_action", "task_id": result.task_id, "payload": result.action.model_dump(mode="json")})
                await websocket.send_json({"type": "task_result", "payload": result.model_dump(mode="json", exclude_none=True)})
            elif message_type == "device_result":
                task_id = str(incoming.get("task_id", ""))
                payload = DeviceResult.model_validate(incoming.get("payload", {}))
                orchestrator.record_device_result(task_id, {"device_id": device_id, **payload.model_dump()})
                await websocket.send_json({"type": "ack", "task_id": task_id})
            elif message_type == "ping":
                await websocket.send_json({"type": "pong"})
            else:
                await websocket.send_json({"type": "error", "message": "Unsupported message type"})
    except WebSocketDisconnect:
        logger.info("Device disconnected: %s", device_id)
    except Exception as exc:
        logger.exception("WebSocket session error")
        await websocket.send_json({"type": "error", "message": f"Session error: {type(exc).__name__}"})
        await websocket.close(code=1011)
EOF

cat > "$ROOT/backend/tests/test_security.py" <<'EOF'
import os

os.environ.setdefault("TOKEN_SECRET", "test-secret-that-is-long-enough")
os.environ.setdefault("PAIRING_CODE", "test-pairing-code-that-is-long-enough")

from app import security  # noqa: E402


def test_device_token_round_trip(monkeypatch):
    monkeypatch.setattr(security.settings, "token_secret", "test-secret-that-is-long-enough")
    token, _ = security.issue_device_token("device-12345678")
    assert security.verify_device_token(token) == "device-12345678"
EOF

cat > "$ROOT/docs/BACKEND_SECURITY.md" <<'EOF'
# Gateway security model

The gateway is designed as a **paired, user-owned control plane**, not as an unattended remote-access service. The Android client starts only after a device-local pairing action and may automatically reconnect only with the locally stored pairing token. Accessibility, screen capture, app launch, and settings changes remain subject to Android permissions and the client’s local approval policy.

| Boundary | Implementation | Purpose |
| --- | --- | --- |
| Pairing | Long, single-purpose pairing code plus HMAC-signed device token | Prevent unpaired clients from opening a control channel. |
| Device actions | Structured action proposals with risk labels | The server cannot directly tap the phone; the phone decides whether and when to execute. |
| Sensitive actions | High-risk local confirmation | Text entry, coordinate taps, app launches, and settings intents cannot be executed as low-risk automation. |
| Browser | Read-only extraction with private-address blocking | Reduces SSRF risk and prevents autonomous form submission. |
| Analysis runner | AST allowlist, time/memory/file limits, clean environment | Supports limited data analysis without exposing gateway secrets. |
| Container | Non-root user, read-only root filesystem, capability drop, resource limits | Limits impact if the service or a dependency fails. |

Do not expose the service on the public Internet without a TLS reverse proxy, firewall rules, a high-entropy `PAIRING_CODE`, a unique `TOKEN_SECRET`, and regular dependency updates.
EOF

cat >> "$ROOT/.gitignore" <<'EOF'

# Agent gateway secrets and state
.env
backend/.pytest_cache/
backend/__pycache__/
backend/app/__pycache__/
*.db
android-agent/.gradle/
android-agent/build/
android-agent/app/build/
EOF

if [ -f "$ROOT/ai assistant " ]; then
  mkdir -p "$ROOT/legacy"
  mv "$ROOT/ai assistant " "$ROOT/legacy/mobile_assistant.py.txt"
fi

printf 'Backend scaffold created at %s\n' "$ROOT/backend"
