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
