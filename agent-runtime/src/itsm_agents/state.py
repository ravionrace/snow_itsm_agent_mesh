"""Graph state (checkpointed in MongoDB as the session store) and the per-request runtime context
(never checkpointed: it carries the user's ServiceNow token and the PII vault)."""

from __future__ import annotations

import operator
import time
from dataclasses import dataclass, field
from typing import Annotated, Any, TypedDict

from .config import Settings
from .llm import LlmGateway
from .observability import TurnTrace
from .toolbox import Caller, Toolbox

EVIDENCE_CAP = 300


def keep_recent(left: list[str] | None, right: list[str] | None) -> list[str]:
    merged = (left or []) + (right or [])
    return merged[-EVIDENCE_CAP:]


class MeshState(TypedDict, total=False):
    # identity and conversation
    user_id: str
    session_id: str
    incoming: str | None                                # graph input: the user's (redacted) message
    transcript: Annotated[list[dict[str, Any]], operator.add]
    # coordinator
    active_agent: str | None
    route: dict[str, Any] | None
    # per-agent Messages API threads, append-only so thinking blocks replay unchanged and the prefix caches
    threads: dict[str, list[dict[str, Any]]]
    # everything the model has seen from tools or the user, for the grounding check
    evidence: Annotated[list[str], keep_recent]
    # structured hand-off records (typed JSON, never free prompt text)
    handoffs: Annotated[list[dict[str, Any]], operator.add]
    handoff_request: dict[str, Any] | None
    # the one write awaiting the user's Confirm / Reject
    pending: dict[str, Any] | None
    # per-request counters for the budget guardrail
    turn: dict[str, Any]
    reply: str | None
    reply_agent: str | None
    next_action: str | None


def new_turn(user_text: str | None) -> dict[str, Any]:
    return {"started": time.time(), "model_calls": 0, "tool_calls": 0, "tokens": 0, "handoffs": 0,
            "grounding_retries": 0, "user_text": user_text}


@dataclass
class TurnContext:
    caller: Caller
    toolbox: Toolbox
    llm: LlmGateway
    settings: Settings
    trace: TurnTrace
    pii_vault: dict[str, str] = field(default_factory=dict)
    access: Any = None  # AccessChecker (the Access agent's source of permissions)
