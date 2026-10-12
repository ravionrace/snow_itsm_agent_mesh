"""MCP client side. Each tool call opens a short session to the owning MCP server carrying the signed-in
user's ServiceNow token, so every server call runs as that user. The servers are stateless, so there is
no long-lived session to share between users."""

from __future__ import annotations

import asyncio
import json
import time
from dataclasses import dataclass, field
from typing import Any, Protocol

import httpx2
from mcp.client.session import ClientSession
from mcp.client.streamable_http import streamable_http_client

from .config import Settings


@dataclass(frozen=True)
class ToolInfo:
    server: str
    name: str
    description: str
    input_schema: dict[str, Any]
    read_only: bool


@dataclass(frozen=True)
class Caller:
    """Who the tool call runs as. Never checkpointed."""

    user_id: str
    sn_token: str
    request_id: str
    session_id: str


@dataclass
class ToolOutcome:
    envelope: dict[str, Any]
    text: str
    is_error: bool
    latency_ms: int

    @property
    def status(self) -> str:
        return str(self.envelope.get("status", "ERROR"))


class Toolbox(Protocol):
    async def catalog(self) -> dict[str, ToolInfo]: ...

    async def call(self, server: str, tool: str, args: dict[str, Any], caller: Caller,
                   idempotency_key: str | None = None) -> ToolOutcome: ...


@dataclass
class McpToolbox:
    settings: Settings
    _catalog: dict[str, ToolInfo] | None = field(default=None, init=False)
    _lock: asyncio.Lock = field(default_factory=asyncio.Lock, init=False)

    def _headers(self, caller: Caller | None, idempotency_key: str | None) -> dict[str, str]:
        h: dict[str, str] = {}
        mesh_key = self.settings.resolved_mesh_key()
        if mesh_key:
            h["X-Mesh-Key"] = mesh_key
        if caller:
            h.update({
                "Authorization": f"Bearer {caller.sn_token}",
                "X-User-Id": caller.user_id,
                "X-Request-Id": caller.request_id,
                "X-Session-Id": caller.session_id,
            })
        if idempotency_key:
            h["X-Idempotency-Key"] = idempotency_key
        return h

    async def catalog(self) -> dict[str, ToolInfo]:
        """Tool name -> ToolInfo across all servers, listed once and cached. Tool names are unique mesh-wide."""
        async with self._lock:
            if self._catalog is None:
                found: dict[str, ToolInfo] = {}
                for server, url in self.settings.mcp_servers.items():
                    async with httpx2.AsyncClient(headers=self._headers(None, None), timeout=15) as hc:
                        async with streamable_http_client(url, http_client=hc) as (r, w):
                            async with ClientSession(r, w) as s:
                                await s.initialize()
                                listed = await s.list_tools()
                    for t in listed.tools:
                        if t.name in found:
                            raise RuntimeError(f"tool name {t.name} is served by both {found[t.name].server} and {server}")
                        ann = t.annotations
                        found[t.name] = ToolInfo(server, t.name, t.description or "", dict(t.input_schema),
                                                 bool(ann and ann.read_only_hint))
                self._catalog = found
            return self._catalog

    async def call(self, server: str, tool: str, args: dict[str, Any], caller: Caller,
                   idempotency_key: str | None = None) -> ToolOutcome:
        url = self.settings.mcp_servers[server]
        timeout = self.settings.mcp_timeouts_s.get(server, 20)
        start = time.monotonic()
        try:
            async with httpx2.AsyncClient(headers=self._headers(caller, idempotency_key), timeout=timeout) as hc:
                async with streamable_http_client(url, http_client=hc) as (r, w):
                    async with ClientSession(r, w, read_timeout_seconds=timeout) as s:
                        await s.initialize()
                        result = await s.call_tool(tool, args)
        except Exception as e:  # transport failure: the write outcome is unknown, never retry blindly
            env = {"status": "ERROR", "code": "TOOL_UNAVAILABLE",
                   "message": f"The {server} service could not be reached ({type(e).__name__}).",
                   "next_step": "Tell the user the system is unavailable. If this was a change, it may or may not "
                                "have been applied; read the record before doing anything else."}
            return ToolOutcome(env, json.dumps(env), True, int((time.monotonic() - start) * 1000))
        text = "".join(getattr(c, "text", "") for c in (result.content or []))
        try:
            envelope = json.loads(text)
        except json.JSONDecodeError:
            envelope = {"status": "ERROR", "code": "BAD_TOOL_RESPONSE", "message": "Unreadable tool response."}
        return ToolOutcome(envelope, text, bool(result.is_error), int((time.monotonic() - start) * 1000))
