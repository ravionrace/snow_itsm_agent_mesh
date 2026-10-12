"""Runs turns through the compiled graph. Shared by the HTTP API, the eval runner and the tests."""

from __future__ import annotations

import asyncio
import uuid
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from .access import AccessChecker, McpAccessChecker
from .config import Settings
from .graph import build_graph, public_pending
from .llm import LlmGateway
from .observability import TraceSink, TurnTrace
from .state import TurnContext
from .toolbox import Caller, Toolbox


class SessionOwnershipError(Exception):
    pass


class NoPendingConfirmation(Exception):
    pass


@dataclass
class TurnResult:
    session_id: str
    request_id: str
    reply: str | None
    agent: str | None
    pending: dict[str, Any] | None
    tokens: int
    cost_usd: float
    trace: TurnTrace = field(repr=False)

    @property
    def steps(self) -> list[dict[str, Any]]:
        return self.trace.steps


def make_checkpointer(settings: Settings) -> BaseCheckpointSaver:
    if settings.mongo_url:
        from langgraph.checkpoint.mongodb import MongoDBSaver
        from pymongo import MongoClient
        return MongoDBSaver(MongoClient(settings.mongo_url), db_name=settings.mongo_db, ttl=settings.session_ttl_s)
    return InMemorySaver()


class MeshRuntime:
    def __init__(self, settings: Settings, toolbox: Toolbox, llm: LlmGateway,
                 checkpointer: BaseCheckpointSaver | None = None, sink: TraceSink | None = None,
                 access: AccessChecker | None = None):
        self.settings = settings
        self.toolbox = toolbox
        self.access = access or McpAccessChecker(toolbox)
        self.llm = llm
        self.graph = build_graph().compile(checkpointer=checkpointer or make_checkpointer(settings))
        self.sink = sink
        self._locks: dict[str, asyncio.Lock] = defaultdict(asyncio.Lock)
        self._running: dict[str, TurnTrace] = {}  # session id -> trace of the turn in progress

    def _config(self, session_id: str) -> dict[str, Any]:
        return {"configurable": {"thread_id": session_id}, "recursion_limit": 60}

    def _context(self, user_id: str, sn_token: str, session_id: str, vault: dict[str, str] | None) -> TurnContext:
        request_id = uuid.uuid4().hex
        return TurnContext(
            caller=Caller(user_id=user_id, sn_token=sn_token, request_id=request_id, session_id=session_id),
            toolbox=self.toolbox, llm=self.llm, settings=self.settings,
            trace=TurnTrace(request_id=request_id, session_id=session_id, user_id=user_id),
            pii_vault=dict(vault or {}),
            access=self.access,
        )

    async def _snapshot(self, session_id: str, user_id: str):
        snap = await self.graph.aget_state(self._config(session_id))
        owner = (snap.values or {}).get("user_id")
        if owner and owner != user_id:
            raise SessionOwnershipError(session_id)
        return snap

    async def send(self, session_id: str, user_id: str, sn_token: str, message: str,
                   vault: dict[str, str] | None = None) -> TurnResult:
        """A new user message. If a preview is still waiting, it is discarded and the message is routed."""
        async with self._locks[session_id]:
            snap = await self._snapshot(session_id, user_id)
            ctx = self._context(user_id, sn_token, session_id, vault)
            if snap.interrupts:
                pending = (snap.values or {}).get("pending") or {}
                payload: Any = Command(resume={"decision": "superseded", "pending_id": pending.get("id"),
                                               "message": message})
            else:
                payload = {"incoming": message}
            return await self._run(session_id, payload, ctx)

    async def decide(self, session_id: str, user_id: str, sn_token: str, pending_id: str, decision: str,
                     vault: dict[str, str] | None = None) -> TurnResult:
        """The user's Confirm / Reject click on a preview."""
        if decision not in ("confirm", "reject"):
            raise ValueError("decision must be confirm or reject")
        async with self._locks[session_id]:
            snap = await self._snapshot(session_id, user_id)
            pending = (snap.values or {}).get("pending")
            if not snap.interrupts or not pending or pending.get("id") != pending_id:
                raise NoPendingConfirmation(pending_id)
            ctx = self._context(user_id, sn_token, session_id, vault)
            ctx.trace.prompt = f"[{decision}] {pending.get('tool')}"
            return await self._run(session_id, Command(resume={"decision": decision, "pending_id": pending_id}), ctx)

    async def history(self, session_id: str, user_id: str) -> dict[str, Any]:
        snap = await self._snapshot(session_id, user_id)
        values = snap.values or {}
        return {"session_id": session_id, "transcript": values.get("transcript", []),
                "active_agent": values.get("active_agent"),
                "pending": public_pending(values.get("pending")) if snap.interrupts else None}

    def progress(self, session_id: str, user_id: str) -> list[dict[str, Any]]:
        """Steps of the turn running in this session so far (empty when none is running)."""
        trace = self._running.get(session_id)
        return list(trace.steps) if trace and trace.user_id == user_id else []

    async def _run(self, session_id: str, payload: Any, ctx: TurnContext) -> TurnResult:
        config = self._config(session_id)
        self._running[session_id] = ctx.trace
        try:
            await self.graph.ainvoke(payload, config, context=ctx)
        finally:
            self._running.pop(session_id, None)
            snap = await self.graph.aget_state(config)
            values = snap.values or {}
            if self.sink is not None:
                self.sink.flush(ctx.trace, values.get("reply"), values.get("reply_agent"))
        return TurnResult(
            session_id=session_id, request_id=ctx.caller.request_id, reply=values.get("reply"),
            agent=values.get("reply_agent"),
            pending=public_pending(values.get("pending")) if snap.interrupts else None,
            tokens=ctx.trace.total_tokens, cost_usd=ctx.trace.total_cost, trace=ctx.trace,
        )
