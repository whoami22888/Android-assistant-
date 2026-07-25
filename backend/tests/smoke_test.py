#!/usr/bin/env python3
"""Dependency-light validation for core gateway components."""
from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

os.environ.setdefault("TOKEN_SECRET", "test-secret-that-is-long-enough")
os.environ.setdefault("PAIRING_CODE", "test-pairing-code-that-is-long-enough")
TEST_ROOT = Path(tempfile.mkdtemp(prefix="agent-gateway-test-"))
os.environ.setdefault("DATABASE_PATH", str(TEST_ROOT / "gateway.db"))
os.environ.setdefault("WORKSPACE_PATH", str(TEST_ROOT / "workspace"))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.database import Database  # noqa: E402
from app.main import app  # noqa: E402
from app.sandbox import PythonSandbox  # noqa: E402
from app.security import issue_device_token, verify_device_token, verify_pairing_code  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402


def main() -> None:
    token, expiry = issue_device_token("device-12345678")
    assert expiry > 0
    assert verify_device_token(token) == "device-12345678"
    assert verify_pairing_code("test-pairing-code-that-is-long-enough")
    assert not verify_pairing_code("not-the-pairing-code")

    with tempfile.TemporaryDirectory() as temporary_directory:
        database = Database(str(Path(temporary_directory) / "agent.db"))
        database.initialize()
        database.upsert_device("device-12345678", "Test Device")
        database.create_task("task-12345678", "device-12345678")
        database.append_event("task-12345678", "test", {"ok": True})
        events = database.recent_events("task-12345678")
        assert events[0]["payload"] == {"ok": True}

        sandbox = PythonSandbox(str(Path(temporary_directory) / "workspace"))
        output = sandbox.run("import math\nprint(math.sqrt(81))")
        assert output["exit_code"] == 0
        assert output["stdout"].strip() == "9.0"

    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        paired = client.post(
            "/v1/pair",
            json={
                "device_id": "device-12345678",
                "device_name": "Test Device",
                "pairing_code": "test-pairing-code-that-is-long-enough",
            },
        )
        assert paired.status_code == 200
        access_token = paired.json()["access_token"]
        task = client.post(
            "/v1/tasks",
            headers={"X-Agent-Token": access_token},
            json={"prompt": "Reply with a short readiness status."},
        )
        assert task.status_code == 200
        assert task.json()["state"] == "completed"

    print("Core gateway smoke test passed")


if __name__ == "__main__":
    main()
