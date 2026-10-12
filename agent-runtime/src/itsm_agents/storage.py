"""Relational storage (MySQL in production, SQLite for local dev): cost tracker, tool-call audit summary and
eval results. Session history and traces live in MongoDB."""

from __future__ import annotations

import datetime as dt
import json
from typing import Any

from sqlalchemy import (JSON, Boolean, Column, DateTime, Float, Integer, MetaData, String, Table, Text,
                        create_engine, insert)
from sqlalchemy.engine import Engine

metadata = MetaData()

cost_ledger = Table(
    "cost_ledger", metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("request_id", String(64), index=True, nullable=False),
    Column("session_id", String(64), index=True, nullable=False),
    Column("user_id", String(128), index=True, nullable=False),
    Column("agent", String(32), index=True, nullable=False),
    Column("model", String(64), nullable=False),
    Column("input_tokens", Integer, nullable=False),
    Column("output_tokens", Integer, nullable=False),
    Column("cache_read_tokens", Integer, nullable=False),
    Column("cache_write_tokens", Integer, nullable=False),
    Column("cost_usd", Float, nullable=False),
    Column("ticket", String(32), index=True),
    Column("created_at", DateTime, nullable=False),
)

tool_call_audit = Table(
    "tool_call_audit", metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("request_id", String(64), index=True, nullable=False),
    Column("session_id", String(64), index=True, nullable=False),
    Column("user_id", String(128), index=True, nullable=False),
    Column("agent", String(32), nullable=False),
    Column("server", String(32), nullable=False),
    Column("tool", String(64), index=True, nullable=False),
    Column("phase", String(16), nullable=False),  # call | preview | commit | blocked
    Column("status", String(32), nullable=False),
    Column("code", String(64)),
    Column("record", String(32), index=True),
    Column("latency_ms", Integer, nullable=False),
    Column("args_json", Text),
    Column("created_at", DateTime, nullable=False),
)

eval_result = Table(
    "eval_result", metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("run_id", String(64), index=True, nullable=False),
    Column("suite", String(32), nullable=False),
    Column("case_id", String(64), nullable=False),
    Column("routing_ok", Boolean),
    Column("tools_ok", Boolean),
    Column("group_match", Boolean),
    Column("safety_ok", Boolean),
    Column("passed", Boolean, nullable=False),
    Column("cost_usd", Float),
    Column("details", JSON),
    Column("created_at", DateTime, nullable=False),
)


class Store:
    def __init__(self, url: str):
        self.engine: Engine = create_engine(url, pool_pre_ping=True, future=True)
        metadata.create_all(self.engine)

    def write(self, table: Table, rows: list[dict[str, Any]]) -> None:
        if not rows:
            return
        now = dt.datetime.now(dt.timezone.utc).replace(tzinfo=None)
        for r in rows:
            r.setdefault("created_at", now)
        with self.engine.begin() as conn:
            conn.execute(insert(table), rows)


def redact_args_for_audit(args: dict[str, Any]) -> str:
    """Audit rows keep argument names and short values only; long free text is truncated."""
    def short(v: Any) -> Any:
        if isinstance(v, str) and len(v) > 200:
            return v[:200] + "..."
        return v
    return json.dumps({k: short(v) for k, v in args.items()}, default=str)[:4000]
