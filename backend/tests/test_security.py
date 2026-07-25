import os

os.environ.setdefault("TOKEN_SECRET", "test-secret-that-is-long-enough")
os.environ.setdefault("PAIRING_CODE", "test-pairing-code-that-is-long-enough")

from app import security  # noqa: E402


def test_device_token_round_trip(monkeypatch):
    monkeypatch.setattr(security.settings, "token_secret", "test-secret-that-is-long-enough")
    token, _ = security.issue_device_token("device-12345678")
    assert security.verify_device_token(token) == "device-12345678"
