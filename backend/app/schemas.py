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
