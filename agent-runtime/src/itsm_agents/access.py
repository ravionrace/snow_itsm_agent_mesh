"""The Access agent's view of the user: which ITSM APIs they may read or write.

Permissions come from the Access MCP server (GET /access/me as the user), which reflects ServiceNow's
group-granted <api>_read / <api>_write roles. The Access agent is deliberately deterministic code, not a
model: authorization is never left to an LLM. ServiceNow's REST ACLs still enforce every call; this layer
refuses early, hides tools the user cannot use, and explains what access to ask for.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

from .toolbox import Caller, Toolbox, ToolInfo

ACCESS_SERVER = "access"

# Which API each MCP server's tools call. The CMDB & KB server fronts two APIs.
SERVER_API = {"incident": "incident", "problem": "problem", "change": "change", "catalog": "catalog", "cmdb_kb": "cmdb"}
TOOL_API = {"search_kb": "kb"}
# Tools whose REST route is a POST but only reads (validate answers against the form).
READ_ONLY_TOOLS = {"validate_variables"}

AGENT_API = {"incident": "incident", "problem": "problem", "change": "change", "catalog": "catalog"}
API_TITLES = {"incident": "Incident", "problem": "Problem", "change": "Change", "catalog": "Service Catalog",
              "cmdb": "CMDB & Directory", "kb": "Knowledge"}


class AccessUnavailable(Exception):
    """Permissions could not be determined. Callers fail closed."""


def tool_api(info: ToolInfo) -> tuple[str, str] | None:
    """(api, level) a tool needs, or None for tools outside the agent APIs."""
    api = TOOL_API.get(info.name) or SERVER_API.get(info.server)
    if api is None:
        return None
    return api, "read" if info.read_only or info.name in READ_ONLY_TOOLS else "write"


@dataclass
class Permissions:
    apis: dict[str, dict[str, Any]] = field(default_factory=dict)
    is_access_admin: bool = False

    def allows(self, api: str, level: str) -> bool:
        p = self.apis.get(api) or {}
        return bool(p.get("write")) if level == "write" else bool(p.get("read") or p.get("write"))

    def allows_tool(self, info: ToolInfo) -> bool:
        need = tool_api(info)
        return need is None or self.allows(*need)

    def granted_by(self, api: str) -> list[str]:
        return [f"{g.get('group')} ({g.get('level')})" for g in (self.apis.get(api) or {}).get("granted_by", [])]

    def denial(self, api: str, level: str) -> dict[str, Any]:
        title = API_TITLES.get(api, api)
        return {"status": "BLOCKED", "code": "ACCESS_DENIED",
                "message": f"You do not have {level} access to the {title} API.",
                "next_step": f"Tell the user they need {level} access to the {title} API; an access admin can add "
                             f"them to a group that has it. Do not try another way to do the same thing."}

    def to_state(self) -> dict[str, Any]:
        return {"apis": self.apis, "is_access_admin": self.is_access_admin}

    @classmethod
    def from_state(cls, data: dict[str, Any]) -> "Permissions":
        return cls(apis=data.get("apis", {}), is_access_admin=bool(data.get("is_access_admin")))

    @classmethod
    def from_me(cls, me: dict[str, Any]) -> "Permissions":
        apis = {a["api"]: {"read": bool(a.get("read")), "write": bool(a.get("write")),
                           "granted_by": a.get("granted_by", [])} for a in me.get("apis", [])}
        return cls(apis=apis, is_access_admin=bool(me.get("is_access_admin")))


class AccessChecker(Protocol):
    async def permissions(self, caller: Caller) -> Permissions: ...


@dataclass
class McpAccessChecker:
    """Asks the Access MCP server, as the user."""

    toolbox: Toolbox

    async def permissions(self, caller: Caller) -> Permissions:
        outcome = await self.toolbox.call(ACCESS_SERVER, "get_my_permissions", {}, caller)
        if outcome.status != "OK" or not isinstance(outcome.envelope.get("data"), dict):
            raise AccessUnavailable(outcome.envelope.get("message") or outcome.status)
        return Permissions.from_me(outcome.envelope["data"])


async def ensure_permissions(checker: AccessChecker, caller: Caller, turn: dict[str, Any]) -> Permissions:
    """Permissions for this turn, fetched once and kept in the turn state (they hold no secrets)."""
    if turn.get("permissions") is None:
        turn["permissions"] = (await checker.permissions(caller)).to_state()
    return Permissions.from_state(turn["permissions"])
