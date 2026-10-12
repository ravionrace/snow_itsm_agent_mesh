#!/usr/bin/env python3
"""Single source of truth for the app's REST surface. Generates, from ROUTES below:

  src/server/rest/handlers.ts            one exported handler per route
  src/fluent/rest/<api>.now.ts           RestApi definitions (v1, ACL per route)
  src/fluent/security/api-access.now.ts  per-API read/write roles + REST endpoint ACLs + agent_user bundle
  src/server/lib/api-catalog.ts          the API catalog the access service and console show

Access levels per route:
  read / write   agent-facing APIs; GET routes need <api>_read, others need <api>_write (write contains read)
  auditor        console and access read views (x_..._itsm_ai.auditor; access_admin contains it)
  access_admin   access changes (grants, groups, membership)
  authenticated  any signed-in user (GET /access/me - the Access MCP asks this for the calling user)
  chat_user      the in-app Assistant (x_..._itsm_ai.chat_user)

Re-run after editing ROUTES: python3 tools/gen-routes.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCOPE = json.loads((ROOT / "now.config.json").read_text())["scope"]

# api -> dict(cls, module, title, desc, agent, routes=[(resource, method, path, service method, summary, access?)])
# For agent APIs access defaults to read for GET and write otherwise.
ROUTES = {
    "incident": dict(cls="IncidentService", module="incident-service", title="Incident", agent=True,
        desc="Incident tools for the agent mesh", routes=[
        ("create", "POST", "/", "create", "Create an incident"),
        ("search", "GET", "/", "search", "Search incidents"),
        ("similar", "GET", "/similar", "similar", "Recent incidents on the same CI or text"),
        ("my_groups", "GET", "/my_groups", "myGroups", "Groups the caller belongs to"),
        ("get", "GET", "/{id}", "get", "Get an incident by number or sys_id"),
        ("update", "PATCH", "/{id}", "update", "Update state, impact, urgency or category"),
        ("add_note", "POST", "/{id}/notes", "addNote", "Add a work note or comment"),
        ("assign", "POST", "/{id}/assign", "assign", "Assign to one of the caller's groups"),
        ("resolve", "POST", "/{id}/resolve", "resolve", "Resolve (never P1)"),
    ]),
    "problem": dict(cls="ProblemService", module="problem-service", title="Problem", agent=True,
        desc="Problem tools for the agent mesh", routes=[
        ("create", "POST", "/", "create", "Create a problem"),
        ("known_errors", "GET", "/known_errors", "knownErrors", "Match symptoms against known errors"),
        ("get", "GET", "/{id}", "get", "Get a problem with linked incidents"),
        ("link_incidents", "POST", "/{id}/incidents", "linkIncidents", "Link incidents"),
        ("root_cause", "POST", "/{id}/root_cause", "rootCause", "Record root cause"),
        ("workaround", "POST", "/{id}/workaround", "workaround", "Record a workaround"),
        ("known_error", "POST", "/{id}/known_error", "knownError", "Mark known error and draft a KB article"),
    ]),
    "change": dict(cls="ChangeService", module="change-service", title="Change", agent=True,
        desc="Change tools for the agent mesh", routes=[
        ("templates", "GET", "/templates", "templates", "Standard change templates"),
        ("cab_schedule", "GET", "/cab_schedule", "cabSchedule", "Upcoming CAB meetings"),
        ("create", "POST", "/", "create", "Create a normal or standard change"),
        ("get", "GET", "/{id}", "get", "Get a change with tasks and approvals"),
        ("risk", "GET", "/{id}/risk", "risk", "Calculate risk"),
        ("conflicts", "GET", "/{id}/conflicts", "conflicts", "Schedule conflicts"),
        ("request_approval", "POST", "/{id}/request_approval", "requestApproval", "Move to Assess and request approval"),
        ("move_to_state", "POST", "/{id}/state", "moveToState", "Implement, review or close"),
    ]),
    "catalog": dict(cls="CatalogService", module="catalog-service", title="Service Catalog", agent=True,
        desc="Service catalog tools for the agent mesh", routes=[
        ("items", "GET", "/items", "items", "Catalog items the caller is entitled to"),
        ("form", "GET", "/items/{id}/form", "form", "Item variables"),
        ("validate", "POST", "/items/{id}/validate", "validate", "Validate answers", "read"),
        ("order", "POST", "/items/{id}/order", "order", "Order now"),
        ("my_requests", "GET", "/my_requests", "myRequests", "The caller's requested items"),
        ("ritm", "GET", "/ritm/{id}", "ritm", "Requested item status"),
        ("cancel", "POST", "/ritm/{id}/cancel", "cancel", "Cancel an open requested item"),
    ]),
    "cmdb": dict(cls="CmdbKbService", module="cmdb-kb-service", title="CMDB & Directory", agent=True,
        desc="CMDB, user and group lookups for the agent mesh", routes=[
        ("ci_by_name", "GET", "/ci", "ci", "Find a CI by name"),
        ("ci", "GET", "/ci/{id}", "ci", "Get a CI"),
        ("impacted_services", "GET", "/ci/{id}/impacted_services", "impactedServices", "Services depending on a CI"),
        ("users", "GET", "/users", "users", "Find users"),
        ("groups", "GET", "/groups", "groups", "Find groups"),
    ]),
    "kb": dict(cls="CmdbKbService", module="cmdb-kb-service", title="Knowledge", agent=True,
        desc="Knowledge search for the agent mesh", routes=[
        ("articles", "GET", "/articles", "articles", "Search published articles"),
    ]),
    "console": dict(cls="ConsoleService", module="console-service", title="Console", agent=False,
        desc="Aggregates for the operations console", routes=[
        ("summary", "GET", "/summary", "summary", "Tool-call statistics", "auditor"),
    ]),
    "chat": dict(cls="ChatProxyService", module="chat-proxy-service", title="Assistant", agent=False,
        desc="The in-app Assistant: relays the signed-in user's chat to the agent mesh", routes=[
        ("start_turn", "POST", "/turns", "startTurn", "Send a message (starts an agent turn)", "chat_user"),
        ("turn", "GET", "/turns/{id}", "turn", "Poll a running turn", "chat_user"),
        ("decide", "POST", "/sessions/{session}/confirmations/{pending}", "decide", "Confirm or reject a pending change", "chat_user"),
        ("session", "GET", "/sessions/{session}", "session", "The conversation so far", "chat_user"),
    ]),
    "access": dict(cls="AccessService", module="access-service", title="Access", agent=False,
        desc="API access: who can read and write each API, and the caller's own permissions", routes=[
        ("me", "GET", "/me", "me", "The caller's permissions per API", "authenticated"),
        ("apis", "GET", "/apis", "apis", "All APIs with the groups that can read and write them", "auditor"),
        ("groups", "GET", "/groups", "groups", "Groups (all, or those holding API access)", "auditor"),
        ("create_group", "POST", "/groups", "createGroup", "Create a group with initial API access", "access_admin"),
        ("members", "GET", "/groups/{id}/members", "members", "Members of a group", "auditor"),
        ("add_member", "POST", "/groups/{id}/members", "addMember", "Add a user to a group", "access_admin"),
        ("remove_member", "POST", "/groups/{id}/members/remove", "removeMember", "Remove a user from a group", "access_admin"),
        ("users", "GET", "/users", "users", "Find users to add", "access_admin"),
        ("grant", "POST", "/grants", "grant", "Grant read or write on an API to a group", "access_admin"),
        ("revoke", "POST", "/grants/revoke", "revoke", "Revoke read or write on an API from a group", "access_admin"),
    ]),
}


def q(text: str) -> str:
    """A single-quoted TypeScript string literal."""
    return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"


def camel(*parts: str) -> str:
    words = "_".join(parts).replace("-", "_").split("_")
    return words[0] + "".join(w.capitalize() for w in words[1:])


def level_of(api: dict, route: tuple) -> str:
    if len(route) > 5:
        return route[5]
    return "read" if route[1] == "GET" else "write"


def acl_var(api_id: str, level: str) -> str:
    return camel(api_id, level, "acl") if level in ("read", "write") else camel(level, "acl")


GENERATED = "// GENERATED by tools/gen-routes.py - edit ROUTES there, then re-run."

# ---------------------------------------------------------------------------------------------- handlers.ts
handlers = [GENERATED,
            "// Relative imports keep the .ts extension: modules are stored on the instance under their full file path.",
            "import { RESTAPIRequest, RESTAPIResponse } from '@servicenow/glide/sn_ws_int'",
            "import { handle } from '../lib/agent-api.ts'"]
modules: dict = {}
for spec in ROUTES.values():
    modules.setdefault(spec["module"], set()).add(spec["cls"])
for module, classes in modules.items():
    handlers.append(f"import {{ {', '.join(sorted(classes))} }} from '../services/{module}.ts'")
handlers.append("")
for api_id, spec in ROUTES.items():
    handlers.append(f"// ---- /api/{SCOPE}/v1/{api_id}")
    for r in spec["routes"]:
        resource, method, _path, fn = r[:4]
        handlers.append(
            f"export function {camel(api_id, resource)}(request: RESTAPIRequest, response: RESTAPIResponse): void {{\n"
            f"    handle(request, response, '{api_id}', '{resource}', '{method}', (api) => new {spec['cls']}(api).{fn}())\n}}\n")
(ROOT / "src/server/rest/handlers.ts").write_text("\n".join(handlers))

# ---------------------------------------------------------------------------------------------- roles + ACLs
access = [GENERATED, "import '@servicenow/sdk/global'", "import { Acl, Role } from '@servicenow/sdk/core'",
          "import { accessAdmin, auditor, chatUser } from './roles.now'", ""]
bundle = []
for api_id, spec in ROUTES.items():
    if not spec["agent"]:
        continue
    levels = {level_of(spec, r) for r in spec["routes"]}
    read_role, write_role = camel(api_id, "read", "role"), camel(api_id, "write", "role")
    access += [f"/** {spec['title']} API ({spec['desc']}). */",
               f"export const {read_role} = Role({{",
               f"    name: '{SCOPE}.{api_id}_read',",
               f"    description: {q('Read access to the ' + spec['title'] + ' API. Grant it to a group.')},",
               "    grantable: true,", "})", ""]
    bundle.append(read_role)
    if "write" in levels:
        access += [f"export const {write_role} = Role({{",
                   f"    name: '{SCOPE}.{api_id}_write',",
                   f"    description: {q('Read and write access to the ' + spec['title'] + ' API. Grant it to a group.')},",
                   f"    containsRoles: [{read_role}],", "    grantable: true,", "})", ""]
        bundle.append(write_role)
    for level in sorted(levels):
        role = read_role if level == "read" else write_role
        access += [f"export const {acl_var(api_id, level)} = Acl({{",
                   f"    $id: Now.ID['acl-{api_id}-{level}'],",
                   "    type: 'rest_endpoint',",
                   f"    name: '{SCOPE}.{api_id}.{level}',",
                   "    operation: 'execute',",
                   f"    roles: [{role}],",
                   f"    description: {q(spec['title'] + ' API ' + level + ' routes.')},",
                   "    protectionPolicy: 'read',", "})", ""]
access += ["/** Full access to every agent API, for groups that should use the whole assistant. */",
           "export const agentUser = Role({",
           f"    name: '{SCOPE}.agent_user',",
           "    description: 'Read and write access to every agent API. Prefer granting per-API roles to groups.',",
           f"    containsRoles: [{', '.join(bundle)}, chatUser],", "    grantable: true,", "})", "",
           "export const auditorAcl = Acl({", "    $id: Now.ID['acl-auditor-api'],", "    type: 'rest_endpoint',",
           f"    name: '{SCOPE}.console.read',", "    operation: 'execute',", "    roles: [auditor],",
           "    description: 'Console and access views: x_..._itsm_ai.auditor (access_admin contains it).',",
           "    protectionPolicy: 'read',", "})", "",
           "export const accessAdminAcl = Acl({", "    $id: Now.ID['acl-access-admin-api'],", "    type: 'rest_endpoint',",
           f"    name: '{SCOPE}.access.admin',", "    operation: 'execute',", "    roles: [accessAdmin],",
           "    description: 'Changing API access, groups and membership.',", "    protectionPolicy: 'read',", "})", "",
           "export const chatUserAcl = Acl({", "    $id: Now.ID['acl-chat-api'],", "    type: 'rest_endpoint',",
           f"    name: '{SCOPE}.chat.use',", "    operation: 'execute',", "    roles: [chatUser],",
           "    description: 'The in-app Assistant (chat relay to the agent mesh).',", "    protectionPolicy: 'read',", "})", "",
           "export const authenticatedAcl = Acl({", "    $id: Now.ID['acl-authenticated-api'],", "    type: 'rest_endpoint',",
           f"    name: '{SCOPE}.access.self',", "    operation: 'execute',", "    script: 'answer = gs.isLoggedIn();',",
           "    description: 'Any signed-in user may read their own API permissions.',", "    protectionPolicy: 'read',", "})", ""]
(ROOT / "src/fluent/security/api-access.now.ts").write_text("\n".join(access))

# ---------------------------------------------------------------------------------------------- RestApi files
for api_id, spec in ROUTES.items():
    routes = spec["routes"]
    names = [camel(api_id, r[0]) for r in routes]
    acls = sorted({acl_var(api_id, level_of(spec, r)) for r in routes})
    default_acl = acl_var(api_id, level_of(spec, routes[0])) if not spec["agent"] else acl_var(api_id, "read")
    lines = [GENERATED, "import '@servicenow/sdk/global'", "import { RestApi } from '@servicenow/sdk/core'",
             f"import {{ {', '.join(acls)} }} from '../security/api-access.now'",
             f"import {{ {', '.join(names)} }} from '../../server/rest/handlers'", "",
             f"/** /api/{SCOPE}/v1/{api_id} - {spec['desc']}. */",
             f"export const {camel(api_id, 'api')} = RestApi({{",
             f"    $id: Now.ID['api-{api_id}'],",
             f"    name: {q('ITSM AI Agent - ' + api_id)},",
             f"    serviceId: '{api_id}',",
             f"    shortDescription: {q(spec['desc'] + '. Runs as the calling user; every call is audited.')},",
             "    consumes: 'application/json',", "    produces: 'application/json',",
             f"    enforceAcl: [{default_acl}],",
             f"    versions: [{{ $id: Now.ID['api-{api_id}-v1'], version: 1, isDefault: true }}],",
             "    routes: ["]
    for r, name in zip(routes, names):
        resource, method, path, _fn, summary = r[:5]
        lines += ["        {", f"            $id: Now.ID['api-{api_id}-{resource}'],", f"            name: '{resource}',",
                  f"            shortDescription: {q(summary + ' [' + level_of(spec, r) + ']')},",
                  f"            method: '{method}',", f"            path: '{path}',", f"            script: {name},",
                  "            version: 1,", "            authorization: true,", "            authentication: true,",
                  f"            enforceAcl: [{acl_var(api_id, level_of(spec, r))}],", "        },"]
    lines += ["    ],", "})", ""]
    (ROOT / f"src/fluent/rest/{api_id}.now.ts").write_text("\n".join(lines))

# ---------------------------------------------------------------------------------------------- api-catalog.ts
catalog = []
for api_id, spec in ROUTES.items():
    if not spec["agent"]:
        continue
    levels = {level_of(spec, r) for r in spec["routes"]}
    catalog.append({
        "api": api_id, "title": spec["title"], "description": spec["desc"],
        "base_path": f"/api/{SCOPE}/v1/{api_id}",
        "read_role": f"{SCOPE}.{api_id}_read",
        "write_role": f"{SCOPE}.{api_id}_write" if "write" in levels else None,
        "routes": [{"name": r[0], "method": r[1], "path": r[2], "summary": r[4], "level": level_of(spec, r)}
                   for r in spec["routes"]],
    })
(ROOT / "src/server/lib/api-catalog.ts").write_text("\n".join([
    GENERATED,
    f"export const SCOPE = '{SCOPE}'",
    "",
    "export type AccessLevel = 'read' | 'write'",
    "",
    "export interface CatalogRoute { name: string; method: string; path: string; summary: string; level: AccessLevel }",
    "",
    "export interface CatalogApi {",
    "    api: string; title: string; description: string; base_path: string",
    "    read_role: string; write_role: string | null; routes: CatalogRoute[]",
    "}",
    "",
    f"export const API_CATALOG: CatalogApi[] = {json.dumps(catalog, indent=4)}",
    ""]))

agent_routes = sum(len(s["routes"]) for s in ROUTES.values() if s["agent"])
print(f"{len(ROUTES)} APIs, {sum(len(s['routes']) for s in ROUTES.values())} routes "
      f"({agent_routes} agent-facing, {len(catalog)} APIs with read/write roles)")
