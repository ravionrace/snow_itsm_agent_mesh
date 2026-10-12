"""One trace per request: the prompt, each model call (tokens, cost, latency) and each tool call (arguments,
result, latency). Spans go to OpenTelemetry (OTLP when OTEL_EXPORTER_OTLP_ENDPOINT is set); the full trace
document goes to MongoDB and the cost / audit summary to MySQL."""

from __future__ import annotations

import datetime as dt
import os
import time
from dataclasses import dataclass, field
from typing import Any

from opentelemetry import trace
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from .config import Price, Settings
from .guardrails import RECORD_RE
from .llm import Usage
from .storage import Store, cost_ledger, redact_args_for_audit, tool_call_audit

_tracer_ready = False


def init_tracing(service_name: str = "itsm-agent-runtime") -> None:
    global _tracer_ready
    if _tracer_ready:
        return
    provider = TracerProvider(resource=Resource.create({"service.name": service_name}))
    if os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT"):
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
        provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
    trace.set_tracer_provider(provider)
    _tracer_ready = True


tracer = trace.get_tracer("itsm_agents")


def cost_usd(prices: dict[str, Price], model: str, usage: Usage) -> float:
    price = prices.get(model) or next((p for m, p in prices.items() if model.startswith(m)), None)
    if price is None:
        return 0.0
    return (usage.input_tokens * price.input + usage.output_tokens * price.output +
            usage.cache_read_tokens * price.cache_read + usage.cache_write_tokens * price.cache_write) / 1_000_000


@dataclass
class TurnTrace:
    """Collected during one request (never checkpointed), flushed by the API after the graph run."""

    request_id: str
    session_id: str
    user_id: str
    started: float = field(default_factory=time.monotonic)
    prompt: str = ""
    model_calls: list[dict[str, Any]] = field(default_factory=list)
    tool_calls: list[dict[str, Any]] = field(default_factory=list)
    events: list[dict[str, Any]] = field(default_factory=list)
    # What the chat shows while the turn runs and under the reply: routing, access, thinking, tool calls.
    steps: list[dict[str, Any]] = field(default_factory=list)

    def model_call(self, agent: str, model: str, usage: Usage, latency_ms: int, prices: dict[str, Price],
                   stop_reason: str) -> float:
        cost = cost_usd(prices, model, usage)
        self.model_calls.append({
            "agent": agent, "model": model, "latency_ms": latency_ms, "stop_reason": stop_reason,
            "input_tokens": usage.input_tokens, "output_tokens": usage.output_tokens,
            "cache_read_tokens": usage.cache_read_tokens, "cache_write_tokens": usage.cache_write_tokens,
            "cost_usd": round(cost, 6),
        })
        return cost

    def tool_call(self, agent: str, server: str, tool: str, phase: str, args: dict[str, Any], status: str,
                  code: str | None, latency_ms: int, result_excerpt: str, flags: list[str] | None = None) -> None:
        self.step("tool", agent=agent, tool=tool, phase=phase, status=status, ms=latency_ms)
        self.tool_calls.append({
            "agent": agent, "server": server, "tool": tool, "phase": phase, "args": args, "status": status,
            "code": code, "latency_ms": latency_ms, "result": result_excerpt[:4000], "injection_flags": flags or [],
            "at": dt.datetime.now(dt.timezone.utc).isoformat(),
        })

    def step(self, kind: str, **data: Any) -> None:
        self.steps.append({"kind": kind, **data})

    def event(self, kind: str, **data: Any) -> None:
        self.events.append({"kind": kind, **data, "at": dt.datetime.now(dt.timezone.utc).isoformat()})

    @property
    def total_cost(self) -> float:
        return round(sum(m["cost_usd"] for m in self.model_calls), 6)

    @property
    def total_tokens(self) -> int:
        return sum(m["input_tokens"] + m["output_tokens"] + m["cache_read_tokens"] + m["cache_write_tokens"]
                   for m in self.model_calls)

    def ticket(self) -> str | None:
        for t in self.tool_calls:
            m = RECORD_RE.search(str(t["args"])) or RECORD_RE.search(t["result"])
            if m:
                return m.group(0)
        return None

    def document(self, reply: str | None, agent: str | None) -> dict[str, Any]:
        return {
            "_id": self.request_id, "session_id": self.session_id, "user_id": self.user_id,
            "created_at": dt.datetime.now(dt.timezone.utc), "duration_ms": int((time.monotonic() - self.started) * 1000),
            "prompt": self.prompt, "reply": reply, "agent": agent, "model_calls": self.model_calls,
            "tool_calls": self.tool_calls, "events": self.events, "total_tokens": self.total_tokens,
            "total_cost_usd": self.total_cost,
        }


class TraceSink:
    def __init__(self, settings: Settings, store: Store | None):
        self.store = store
        self.collection = None
        if settings.mongo_url:
            from pymongo import MongoClient
            self.collection = MongoClient(settings.mongo_url)[settings.mongo_db]["traces"]

    def flush(self, t: TurnTrace, reply: str | None, agent: str | None) -> None:
        if self.collection is not None:
            self.collection.replace_one({"_id": t.request_id}, t.document(reply, agent), upsert=True)
        if self.store is None:
            return
        ticket = t.ticket()
        self.store.write(cost_ledger, [{
            "request_id": t.request_id, "session_id": t.session_id, "user_id": t.user_id, "agent": m["agent"],
            "model": m["model"], "input_tokens": m["input_tokens"], "output_tokens": m["output_tokens"],
            "cache_read_tokens": m["cache_read_tokens"], "cache_write_tokens": m["cache_write_tokens"],
            "cost_usd": m["cost_usd"], "ticket": ticket,
        } for m in t.model_calls])
        self.store.write(tool_call_audit, [{
            "request_id": t.request_id, "session_id": t.session_id, "user_id": t.user_id, "agent": c["agent"],
            "server": c["server"], "tool": c["tool"], "phase": c["phase"], "status": c["status"], "code": c["code"],
            "record": (RECORD_RE.search(str(c["args"])) or RECORD_RE.search(c["result"]) or [None])[0],
            "latency_ms": c["latency_ms"], "args_json": redact_args_for_audit(c["args"]),
        } for c in t.tool_calls])
