"""Integration: LangGraph runtime -> real Java MCP servers -> mock ServiceNow. Only the LLM is scripted.

Builds on `mvn package` output. Enable with MESH_IT=1:
    (cd .. && mvn -q -DskipTests package) && MESH_IT=1 pytest tests/integration -q
"""

from __future__ import annotations

import os
import socket
import subprocess
import time
from pathlib import Path

import pytest

from itsm_agents.agents import RouteDecision
from itsm_agents.config import Settings
from itsm_agents.fakes import ScriptedLlm, text, tool_use
from itsm_agents.runtime import MeshRuntime
from itsm_agents.toolbox import McpToolbox

from .mock_servicenow import MockServiceNow

pytestmark = pytest.mark.skipif(os.environ.get("MESH_IT") != "1", reason="set MESH_IT=1 to run integration tests")

ROOT = Path(__file__).resolve().parents[3]
MESH_KEY = "it-mesh-key"


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _start(module: str, port: int, sn_url: str) -> subprocess.Popen:
    jar = next((ROOT / module / "target").glob(f"{module}-*.jar"))
    proc = subprocess.Popen(
        ["java", "-jar", str(jar), f"--server.port={port}", f"--itsm.mcp.servicenow.instance-url={sn_url}",
         f"--itsm.mcp.mesh-key={MESH_KEY}", "--itsm.mcp.confirmation.secret=integration-test-secret-0123456789abcdef"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(60):
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return proc
        except OSError:
            time.sleep(0.5)
    proc.kill()
    raise RuntimeError(f"{module} did not start")


@pytest.fixture(scope="module")
def mesh():
    sn = MockServiceNow()
    sn_url = f"http://127.0.0.1:{sn.port}"
    ports = {"incident": _free_port(), "cmdb_kb": _free_port(), "access": _free_port()}
    procs = [_start("mcp-incident", ports["incident"], sn_url), _start("mcp-cmdb-kb", ports["cmdb_kb"], sn_url),
             _start("mcp-access", ports["access"], sn_url)]
    settings = Settings(mongo_url="", mcp_mesh_key=MESH_KEY,
                        mcp_servers={k: f"http://127.0.0.1:{p}/mcp" for k, p in ports.items()})
    yield sn, settings
    for p in procs:
        p.terminate()
    sn.close()


def _route(_):
    return RouteDecision(agent="incident", off_topic=False, confidence=0.9, reason="it")


async def test_preview_confirm_commit_through_real_mcp(mesh):
    sn, settings = mesh
    llm = ScriptedLlm(router=_route, script={"incident": [
        [tool_use("get_incident", {"number": "INC0012345"})],
        [tool_use("resolve_incident", {"number": "INC0012345", "close_code": "Solution provided",
                                       "close_notes": "Fix deployed"})],
        text("Ready to resolve INC0012345. Please confirm."),
        text("INC0012345 is now Resolved."),
    ]})
    toolbox = McpToolbox(settings)
    catalog = await toolbox.catalog()
    assert catalog["resolve_incident"].server == "incident" and not catalog["resolve_incident"].read_only
    assert catalog["get_incident"].read_only
    assert "confirmation_id" not in str(llm.seen)

    rt = MeshRuntime(settings, toolbox, llm)
    r1 = await rt.send("it-session-0001", "alice", "sn-user-token", "Close INC0012345, the fix is deployed.")
    assert r1.pending and r1.pending["tool"] == "resolve_incident"
    assert not any(r["path"].endswith("/resolve") for r in sn.requests)       # preview wrote nothing
    assert all(r["auth"] == "Bearer sn-user-token" for r in sn.requests)      # always as the user
    assert r1.trace.tool_calls[0]["injection_flags"]                           # planted note flagged

    r2 = await rt.decide("it-session-0001", "alice", "sn-user-token", r1.pending["id"], "confirm")
    resolves = [r for r in sn.requests if r["path"].endswith("/resolve")]
    assert len(resolves) == 1
    assert resolves[0]["body"] == {"close_code": "Solution provided", "close_notes": "Fix deployed"}
    assert resolves[0]["idempotency"].startswith("confirm-")
    assert "Resolved" in r2.reply


async def test_p1_resolve_blocked_by_mcp_rule(mesh):
    sn, settings = mesh
    llm = ScriptedLlm(router=_route, script={"incident": [
        [tool_use("resolve_incident", {"number": "INC0012399", "close_code": "Solution provided", "close_notes": "x"})],
        text("INC0012399 is a P1, so it has to be resolved manually by the major incident manager."),
    ]})
    rt = MeshRuntime(settings, McpToolbox(settings), llm)
    r = await rt.send("it-session-0002", "alice", "sn-user-token", "resolve INC0012399")
    assert r.pending is None
    assert r.trace.tool_calls[0]["code"] == "P1_RESOLVE"
    assert not any(req["path"].endswith("INC0012399/resolve") for req in sn.requests)


async def test_access_agent_uses_access_mcp_for_read_only_user(mesh):
    sn, settings = mesh
    llm = ScriptedLlm(router=_route, script={"incident": [
        [tool_use("get_incident", {"number": "INC0012345"})],
        [tool_use("resolve_incident", {"number": "INC0012345", "close_code": "Solution provided", "close_notes": "x"})],
        text("You have read access only, so I can't resolve INC0012345."),
    ]})
    rt = MeshRuntime(settings, McpToolbox(settings), llm)        # default checker = real Access MCP
    before = len(sn.requests)
    r = await rt.send("it-session-0004", "carol", "sn-readonly-token", "resolve INC0012345")
    calls = sn.requests[before:]
    assert any(c["path"].endswith("/access/me") and c["auth"] == "Bearer sn-readonly-token" for c in calls)
    assert "resolve_incident" not in llm.seen[0]["tools"]
    assert not any(c["path"].endswith("/resolve") for c in calls)
    assert r.trace.tool_calls[-1]["code"] == "ACCESS_DENIED"


async def test_wrong_token_is_rejected_by_servicenow(mesh):
    _, settings = mesh
    llm = ScriptedLlm(router=_route, script={"incident": [
        [tool_use("get_incident", {"number": "INC0012345"})], text("You need to sign in again.")]})
    rt = MeshRuntime(settings, McpToolbox(settings), llm)
    r = await rt.send("it-session-0003", "bob", "stolen-token", "show INC0012345")
    # ServiceNow rejects the token on the permission check, so the Access agent fails closed before any tool runs
    assert "can't check your ServiceNow permissions" in r.reply
    assert r.trace.tool_calls == []
