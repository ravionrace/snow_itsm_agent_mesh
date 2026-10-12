"""The Access agent: permissions come from the Access MCP (here a StaticAccess double) and are enforced in code."""

from __future__ import annotations

import json

from itsm_agents.agents import RouteDecision
from itsm_agents.fakes import FixtureToolbox, ScriptedLlm, StaticAccess, text, tool_use
from itsm_agents.config import Settings
from itsm_agents.runtime import MeshRuntime

INC = {"status": "OK", "data": {"number": "INC0012345", "state": "In Progress", "priority_code": "3"}}


def route_incident(_):
    return RouteDecision(agent="incident", off_topic=False, confidence=0.9, reason="t")


def make(llm, toolbox, access):
    return MeshRuntime(Settings(mongo_url=""), toolbox, llm, access=access)


async def test_no_read_access_is_refused_before_any_agent_call():
    llm = ScriptedLlm(router=route_incident, script={"incident": [text("should not run")]})
    toolbox = FixtureToolbox()
    r = await make(llm, toolbox, StaticAccess({"catalog": "write"})).send("s-acc-0001", "bob", "t", "close INC0012345")
    assert r.agent == "access"
    assert "Incident API" in r.reply
    assert llm.seen == [] and toolbox.calls == []


async def test_read_only_user_never_sees_or_runs_write_tools():
    toolbox = FixtureToolbox(fixtures={"get_incident": INC})
    llm = ScriptedLlm(router=route_incident, script={"incident": [
        [tool_use("get_incident", {"number": "INC0012345"})],
        [tool_use("resolve_incident", {"number": "INC0012345", "close_code": "Duplicate", "close_notes": "x"})],
        text("You need write access to resolve INC0012345."),
    ]})
    access = StaticAccess({"incident": "read", "cmdb": "read", "kb": "read"})
    r = await make(llm, toolbox, access).send("s-acc-0002", "bob", "t", "resolve INC0012345")
    offered = llm.seen[0]["tools"]
    assert "get_incident" in offered and "search_kb" in offered
    assert "resolve_incident" not in offered and "assign_to_group" not in offered
    assert [c["tool"] for c in toolbox.calls] == ["get_incident"]          # resolve never reached MCP
    assert r.trace.tool_calls[-1]["code"] == "ACCESS_DENIED"
    assert r.pending is None
    assert access.calls == 1                                                # fetched once per turn


async def test_cmdb_tools_hidden_without_cmdb_access():
    llm = ScriptedLlm(router=route_incident, script={"incident": [text("ok")]})
    await make(llm, FixtureToolbox(), StaticAccess({"incident": "write"})).send("s-acc-0003", "bob", "t", "hi")
    offered = llm.seen[0]["tools"]
    assert "resolve_incident" in offered
    assert not {"get_ci", "lookup_user", "search_kb"} & set(offered)


async def test_access_revoked_between_preview_and_confirm_writes_nothing():
    toolbox = FixtureToolbox(fixtures={"get_incident": INC})
    llm = ScriptedLlm(router=route_incident, script={"incident": [
        [tool_use("add_work_note", {"number": "INC0012345", "text": "called user"})],
        text("Confirm the note on INC0012345?"),
        text("Your access changed, so nothing was added to INC0012345."),
    ]})
    access = StaticAccess({"incident": "write"})
    rt = make(llm, toolbox, access)
    r1 = await rt.send("s-acc-0004", "bob", "t", "note on INC0012345")
    assert r1.pending
    access.grants = {"incident": "read"}                                    # admin revokes write meanwhile
    await rt.decide("s-acc-0004", "bob", "t", r1.pending["id"], "confirm")
    assert toolbox.commits() == []
    assert "no longer have write access" in json.dumps(llm.seen[-1]["messages"])


async def test_access_check_unavailable_fails_closed():
    llm = ScriptedLlm(router=route_incident, script={"incident": [text("should not run")]})
    toolbox = FixtureToolbox()
    r = await make(llm, toolbox, StaticAccess(unavailable=True)).send("s-acc-0005", "bob", "t", "show INC0012345")
    assert "can't check your ServiceNow permissions" in r.reply
    assert llm.seen == [] and toolbox.calls == []


async def test_handoff_target_needs_its_own_read_access():
    toolbox = FixtureToolbox(fixtures={"find_similar": {"status": "OK", "data": []}})
    llm = ScriptedLlm(router=route_incident, script={
        "incident": [[tool_use("handoff", {"target": "problem", "reason": "cluster", "summary": "4 incidents"})]],
        "problem": [text("should not run")],
    })
    r = await make(llm, toolbox, StaticAccess({"incident": "write"})).send("s-acc-0006", "bob", "t", "lots of VPN tickets")
    assert r.agent == "access" and "Problem API" in r.reply
    assert all(s["agent"] != "problem" for s in llm.seen)
