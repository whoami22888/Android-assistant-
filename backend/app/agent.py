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
