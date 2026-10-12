"""HTTP API of the agent runtime. Only the Spring Boot orchestrator API calls it: it has already validated the
user's SSO token, exchanged it for a ServiceNow token, redacted PII and screened the input."""

from __future__ import annotations

import hmac
import re
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from .config import get_settings
from .llm import DefaultGateway
from .observability import TraceSink, init_tracing
from .runtime import MeshRuntime, NoPendingConfirmation, SessionOwnershipError, TurnResult
from .storage import Store
from .toolbox import McpToolbox

SESSION_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


class TurnRequest(BaseModel):
    message: str = Field(min_length=1, max_length=8000)
    pii_vault: dict[str, str] = Field(default_factory=dict)


class DecisionRequest(BaseModel):
    decision: str = Field(pattern="^(confirm|reject)$")
    pii_vault: dict[str, str] = Field(default_factory=dict)


class TurnResponse(BaseModel):
    session_id: str
    request_id: str
    reply: str | None
    agent: str | None
    pending: dict | None
    tokens: int
    cost_usd: float
    steps: list[dict] = Field(default_factory=list)

    @classmethod
    def of(cls, r: TurnResult) -> "TurnResponse":
        return cls(session_id=r.session_id, request_id=r.request_id, reply=r.reply, agent=r.agent,
                   pending=r.pending, tokens=r.tokens, cost_usd=r.cost_usd, steps=r.steps)


class Identity(BaseModel):
    user_id: str
    sn_token: str


_runtime: MeshRuntime | None = None


def runtime() -> MeshRuntime:
    assert _runtime is not None
    return _runtime


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _runtime
    settings = get_settings()
    problems = settings.production_problems()
    if problems:
        raise RuntimeError("Refusing to start in production:\n - " + "\n - ".join(problems))
    init_tracing()
    store = Store(settings.resolved_database_url())
    llm = DefaultGateway(fallbacks=settings.anthropic_fallbacks, api_key=settings.resolved_anthropic_api_key())
    _runtime = MeshRuntime(settings, McpToolbox(settings), llm,
                           sink=TraceSink(settings, store))
    yield


app = FastAPI(title="ITSM agent runtime", lifespan=lifespan)


def caller_id(
    x_runtime_key: Annotated[str | None, Header()] = None,
    x_user_id: Annotated[str | None, Header()] = None,
) -> str:
    """The orchestrator's key and the user it acts for (enough for read-only views of the user's own session)."""
    expected = get_settings().resolved_runtime_api_key()
    if expected and not hmac.compare_digest(expected, x_runtime_key or ""):
        raise HTTPException(401, "invalid runtime key")
    if not x_user_id:
        raise HTTPException(401, "missing user identity")
    return x_user_id


def identity(
    user_id: Annotated[str, Depends(caller_id)],
    x_sn_token: Annotated[str | None, Header()] = None,
) -> Identity:
    if not x_sn_token:
        raise HTTPException(401, "missing user identity")
    return Identity(user_id=user_id, sn_token=x_sn_token)


def _session(session_id: str) -> str:
    if not SESSION_RE.match(session_id):
        raise HTTPException(400, "invalid session id")
    return session_id


@app.post("/v1/sessions/{session_id}/turns", response_model=TurnResponse)
async def post_turn(session_id: str, body: TurnRequest, who: Identity = Depends(identity)) -> TurnResponse:
    try:
        r = await runtime().send(_session(session_id), who.user_id, who.sn_token, body.message, body.pii_vault)
    except (SessionOwnershipError, PermissionError):
        raise HTTPException(404, "session not found")
    return TurnResponse.of(r)


@app.post("/v1/sessions/{session_id}/confirmations/{pending_id}", response_model=TurnResponse)
async def post_decision(session_id: str, pending_id: str, body: DecisionRequest,
                        who: Identity = Depends(identity)) -> TurnResponse:
    try:
        r = await runtime().decide(_session(session_id), who.user_id, who.sn_token, pending_id, body.decision,
                                   body.pii_vault)
    except SessionOwnershipError:
        raise HTTPException(404, "session not found")
    except NoPendingConfirmation:
        raise HTTPException(409, "no such pending confirmation")
    return TurnResponse.of(r)


@app.get("/v1/sessions/{session_id}")
async def get_session(session_id: str, who: Identity = Depends(identity)) -> dict:
    try:
        return await runtime().history(_session(session_id), who.user_id)
    except SessionOwnershipError:
        raise HTTPException(404, "session not found")


@app.get("/v1/sessions/{session_id}/progress")
async def get_progress(session_id: str, user_id: str = Depends(caller_id)) -> dict:
    """What the running turn has done so far: routing, access check, thinking, tool calls."""
    return {"steps": runtime().progress(_session(session_id), user_id)}


@app.get("/healthz")
async def health() -> dict:
    return {"status": "ok"}


def main() -> None:
    import uvicorn
    uvicorn.run("itsm_agents.api:app", host="0.0.0.0", port=8090)
