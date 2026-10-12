"""Test doubles shared by the unit tests and the offline eval replay: a fixture-backed MCP toolbox that never
writes to ServiceNow, and a scripted LLM gateway."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, Callable

from pydantic import BaseModel

from .access import AccessUnavailable, Permissions
from .config import ModelProfile
from .llm import LlmResult, StructuredResult, Usage
from .toolbox import Caller, ToolInfo, ToolOutcome

# Tool catalogue mirroring the MCP servers (name -> (server, read_only)).
CATALOG: dict[str, tuple[str, bool]] = {
    **{t: ("incident", ro) for t, ro in [
        ("create_incident", False), ("get_incident", True), ("search_incidents", True), ("update_incident", False),
        ("add_work_note", False), ("assign_to_group", False), ("resolve_incident", False), ("find_similar", True)]},
    **{t: ("problem", ro) for t, ro in [
        ("create_problem", False), ("get_problem", True), ("link_incidents", False), ("record_root_cause", False),
        ("add_workaround", False), ("create_known_error", False), ("search_known_errors", True)]},
    **{t: ("change", ro) for t, ro in [
        ("get_std_templates", True), ("create_change", False), ("get_change", True), ("assess_risk", True),
        ("check_conflicts", True), ("get_cab_schedule", True), ("request_approval", False), ("move_to_state", False)]},
    **{t: ("catalog", ro) for t, ro in [
        ("search_catalog", True), ("get_item_form", True), ("validate_variables", True), ("order_item", False),
        ("get_ritm_status", True), ("list_my_requests", True), ("cancel_request", False)]},
    **{t: ("cmdb_kb", True) for t in ["get_ci", "get_impacted_services", "search_kb", "lookup_user", "lookup_group"]},
}

Handler = Callable[[dict[str, Any]], dict[str, Any]]


@dataclass
class FixtureToolbox:
    """Answers tool calls from fixtures. Write tools return a preview; a call with a confirmation id 'commits'
    only in memory (fixture key "<tool>:commit" overrides the response) and is recorded, so tests and evals can
    assert exactly what would have been written."""

    fixtures: dict[str, Any] = field(default_factory=dict)
    calls: list[dict[str, Any]] = field(default_factory=list)

    async def catalog(self) -> dict[str, ToolInfo]:
        return {name: ToolInfo(server, name, f"{name} tool", {"type": "object", "properties": {
            "number": {"type": "string"}, "confirmation_id": {"type": "string"}}}, ro)
                for name, (server, ro) in CATALOG.items()}

    async def call(self, server: str, tool: str, args: dict[str, Any], caller: Caller,
                   idempotency_key: str | None = None) -> ToolOutcome:
        self.calls.append({"server": server, "tool": tool, "args": dict(args), "user": caller.user_id,
                           "token": caller.sn_token, "idempotency_key": idempotency_key})
        read_only = CATALOG.get(tool, ("", True))[1]
        if not read_only and "confirmation_id" in args:
            env = self.fixtures.get(f"{tool}:commit") or {
                "status": "OK", "committed": True,
                "data": {"tool": tool, "args": {k: v for k, v in args.items() if k != "confirmation_id"}}}
        else:
            fx = self.fixtures.get(tool)
            if callable(fx):
                env = fx(args)
            elif isinstance(fx, list) and fx:
                env = fx.pop(0)
            elif isinstance(fx, dict):
                env = fx
            elif not read_only:
                env = self._preview(tool, args)  # writes default to a preview
            else:
                env = {"status": "ERROR", "code": "NO_FIXTURE", "message": f"no fixture for {tool}"}
        text = json.dumps(env)
        return ToolOutcome(env, text, env.get("status") in ("ERROR", "BLOCKED"), 1)

    @staticmethod
    def _preview(tool: str, args: dict[str, Any]) -> dict[str, Any]:
        return {"status": "PENDING_CONFIRMATION", "confirmation_id": f"cid-{tool}",
                "expires_at": "2999-01-01T00:00:00Z",
                "preview": {"action": tool, "record": args.get("number", "(new)"),
                            "changes": [{"field": k, "to": str(v)} for k, v in args.items()]}}

    def commits(self) -> list[dict[str, Any]]:
        return [c for c in self.calls if "confirmation_id" in c["args"]]


def text(t: str) -> list[dict]:
    return [{"type": "text", "text": t}]


def tool_use(name: str, args: dict[str, Any], tid: str | None = None) -> dict:
    return {"type": "tool_use", "id": tid or f"tu_{name}", "name": name, "input": args}


@dataclass
class ScriptedLlm:
    """Returns pre-written assistant turns per agent, and a fixed or computed route decision."""

    script: dict[str, list[list[dict]]] = field(default_factory=dict)
    router: Callable[[str], BaseModel] | None = None
    seen: list[dict[str, Any]] = field(default_factory=list)

    async def complete(self, profile: ModelProfile, system: str, messages: list[dict], tools: list[dict]) -> LlmResult:
        agent = next((a for a in ("incident", "problem", "change", "catalog") if f"You are the {a.capitalize()} agent" in system), "?")
        self.seen.append({"agent": agent, "tools": [t["name"] for t in tools], "messages": messages, "model": profile.model})
        queue = self.script.get(agent) or [text("(no script)")]
        content = queue.pop(0) if len(queue) > 1 else queue[0]
        stop = "tool_use" if any(b["type"] == "tool_use" for b in content) else "end_turn"
        return LlmResult(content, stop, Usage(100, 20), profile.model, 5)

    async def structured(self, profile: ModelProfile, system: str, user: str, schema: type) -> StructuredResult:
        assert self.router is not None
        return StructuredResult(self.router(user), Usage(50, 10), profile.model, 3)


ALL_APIS = ("incident", "problem", "change", "catalog", "cmdb", "kb")


@dataclass
class StaticAccess:
    """Fixed permissions for tests and offline evals. `grants` maps api -> 'read' | 'write'."""

    grants: dict[str, str] = field(default_factory=dict)
    unavailable: bool = False
    calls: int = 0

    @classmethod
    def allow_all(cls) -> "StaticAccess":
        return cls({api: "write" for api in ALL_APIS})

    async def permissions(self, caller: Caller) -> Permissions:
        self.calls += 1
        if self.unavailable:
            raise AccessUnavailable("access server down")
        return Permissions(apis={api: {"read": True, "write": level == "write",
                                       "granted_by": [{"group": f"{api} {level}ers", "level": level}]}
                                 for api, level in self.grants.items()})
