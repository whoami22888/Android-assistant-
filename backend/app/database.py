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
