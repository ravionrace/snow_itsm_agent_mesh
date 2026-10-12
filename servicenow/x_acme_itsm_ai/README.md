# x_1320561_itsm_ai — ITSM AI Agent API (now-sdk app)

The scoped app is the only part of the agent mesh that runs inside ServiceNow. It is a ServiceNow SDK (Fluent)
project with two layers, laid out like ServiceNow's `restapi-sample` and `react-components-vite-sample`:

- **REST layer**: seven Scripted REST APIs (`RestApi` in Fluent) with handlers in TypeScript server modules.
  The five MCP servers call these, always with the signed-in user's token.
- **React console**: a UI page (`UiPage` + Vite + `@servicenow/react-components`) for auditors. It shows
  tool-call statistics, the audit trail, and an API explorer that calls the same REST layer as the viewer.

```
now.config.json             scope x_1320561_itsm_ai
now.dev.mjs / now.prebuild.mjs / vite.config.mjs   Vite dev server and UI build (from the SDK sample)
tools/gen-routes.py         single route table → src/server/rest/handlers.ts + src/fluent/rest/*.now.ts
src/fluent/
  rest/*.now.ts             RestApi: incident, problem, change, catalog, cmdb, kb, console (v1, ACL-enforced)
  security/roles.now.ts     x_1320561_itsm_ai.agent_user, x_1320561_itsm_ai.auditor
  security/acls.now.ts      REST endpoint ACLs, UI page ACL, audit table ACLs
  security/cross-scope.now.ts   cross-scope privileges for global tables and change script includes
  tables/tool-audit.now.ts  x_1320561_itsm_ai_tool_audit
  properties.now.ts         cause-CI field, known-error knowledge base
  ui-pages/agent-console.now.ts  x_1320561_itsm_ai_agent_console.do
  navigation.now.ts         application menu + modules
src/server/
  lib/agent-api.ts          error handling, audit row per call, idempotent replay, readable-fields-only JSON
  services/*.ts             incident, problem, change (+ change-apis adapters), catalog, cmdb-kb, console
  rest/handlers.ts          one exported handler per route (generated)
  types/platform-apis.d.ts  types for global / sn_chg_rest script includes not in @servicenow/glide
src/client/                 React console (index.html, app.tsx, components/, services/, utils/)
```

## Who can use which API

Each agent API has two roles: `x_1320561_itsm_ai.<api>_read` and `x_1320561_itsm_ai.<api>_write`.
`write` contains `read`. CMDB and Knowledge are read-only, so they have only a read role. Access is granted
to **groups**, never to individual users, so it follows ServiceNow's normal group → role inheritance:

| Who | Can |
|---|---|
| Members of a group with `<api>_read` | Call that API's GET routes, which is what the agents do on their behalf |
| Members of a group with `<api>_write` | Also create and update through it, always after the preview and Confirm step |
| `x_1320561_itsm_ai.auditor` | See the console, the audit trail and who has access |
| `x_1320561_itsm_ai.access_admin` | Grant or revoke read/write per API, create groups, add or remove members |
| `x_1320561_itsm_ai.agent_user` | Every API, read and write (a convenience bundle; prefer per-API grants) |

**The platform enforces this.** Every route's REST ACL requires the matching role, whoever calls it. In the
agent mesh, the **Access agent** also checks early, using the Access MCP server (`GET /access/me`, called
with the user's token). It refuses requests for APIs the user can't read, hides tools they can't use, and
re-checks before each call and again on Confirm.

The access admin works only on this app's API roles and on groups that hold at least one of them. Those rules
are checked in code (`src/server/services/access-service.ts`) as well as by the route ACL, and every change is
audited. Access admins don't need `user_admin`.

### Admin workflow

1. Give your access administrators `x_1320561_itsm_ai.access_admin`. This is the only manual role
   assignment.
2. Open **ITSM AI Agent → API Access**, or the console at `?view=access`.
3. Use **Grant access** on an API card to give an existing group read or write. Or use **New group**, which
   creates a group with its first grant.
4. Pick the group under *Groups with API access*, then **Add a member** (type-ahead user search) or remove
   members.
5. **Print** gives a printable list of every API, its routes, and who can read or write it.

## REST layer

| API | Base path | Used by |
|---|---|---|
| `incident` | `/api/x_1320561_itsm_ai/v1/incident` | Incident MCP |
| `problem` | `/api/x_1320561_itsm_ai/v1/problem` | Problem MCP |
| `change` | `/api/x_1320561_itsm_ai/v1/change` | Change MCP |
| `catalog` | `/api/x_1320561_itsm_ai/v1/catalog` | Catalog MCP |
| `cmdb`, `kb` | `/api/x_1320561_itsm_ai/v1/cmdb`, `/kb` | CMDB & Knowledge MCP |
| `console` | `/api/x_1320561_itsm_ai/v1/console` | React console |
| `access` | `/api/x_1320561_itsm_ai/v1/access` | React console (API access), Access MCP (`/me`) |

Every route has authentication and ACL authorization on. Agent API routes need the API's read role (GET) or
write role (changes). The console and access views need `auditor`, access changes need `access_admin`, and
`/access/me` is open to any signed-in user. Handlers use `GlideRecordSecure`, so the caller's
own table and field ACLs apply. Only readable fields are returned, and each call writes an audit row. A
repeated `X-Idempotency-Key` on a write replays the stored response instead of writing twice. The coded rules
are repeated here so they hold even if every layer in front is bypassed: no P1 resolve, own groups only, no
emergency changes, change state order.

Routes are defined once, in `tools/gen-routes.py`. It generates the handlers, the `RestApi` definitions,
the per-API roles and ACLs, and the API catalog the console shows. After changing a route, run
`python3 tools/gen-routes.py` and update the matching MCP server.

## React console

`/x_1320561_itsm_ai_agent_console.do`, also under **ITSM AI Agent → Operations Console**. Views are
addressable by URL:

| URL | View |
|---|---|
| `?view=overview&hours=24` | Calls, errors, writes, idempotent replays, latency, by API and resource (refreshes every minute) |
| `?view=access&group=<sys_id>` | Every API with its routes and the groups that can read or write it; my access; groups and members (admins can edit) |
| `?view=audit&filter=errors` | `NowRecordListConnected` on the audit table (all, errors, writes, replays) |
| `?view=record&id=<sys_id>` | Read-only audit record (`RecordProvider` + `FormColumnLayout`) |
| `?view=explorer&resource=incident.search` | Calls read-only agent APIs as the viewer, to check what an agent acting for them would get |

### State management: Redux Toolkit + redux-observable

`src/client/store` holds all server data and request state. Components dispatch commands and select state;
they never fetch data themselves. Each feature file (`store/features/*.ts`) contains its commands, state and
epics. A small kit (`store/kit.ts`) removes the boilerplate:

```ts
export const access = commands('access', { loadApis: none, grant: payload<Grant>(), ... })

const endpoints = combineEpics(
//            when ...             call ...                                          then ...
    endpoint(access.loadApis,     ()  => get('/access/apis'),                       loaded.apisLoaded),
    endpoint(access.grant,        (g) => post('/access/grants', g),                 access.changed, 'concat'),
    endpoint(access.createGroup,  (g) => post('/access/groups', g),                 access.changed, 'exhaust'),
)
react(access.changed, () => [access.loadApis(), access.loadGroups(), access.loadMe()])
```

`endpoint()` handles pending/error state for every command (`selectPending(state, access.grant)`,
`<RequestErrors commands={[...]}/>`) and the flattening strategy:

| Strategy | Use for |
|---|---|
| `switch` (default) | Loads; stale calls are cancelled |
| `concat` | Changes that must apply in order |
| `exhaust` | Submit buttons; repeats are ignored while busy |
| `merge` | Concurrent per-key loads |

Only genuinely stream-shaped logic is written out as a full epic: type-ahead search (debounce, distinct,
cancel, clear), overview auto-refresh (timer until the view unmounts), and the explorer's raw responses.
`npm test` runs the epic tests (Vitest + RxJS `TestScheduler`).

## Build, develop, deploy

```bash
npm install
npm run build                  # type-checks Fluent + server modules, builds the React bundle, packages dist/
npx now-sdk auth --add <instance> --type basic   # once per instance (or OAuth)
npm run deploy                 # now-sdk install → installs the app on the instance
npm run dev                    # Vite dev server on http://localhost:3000, proxied to the instance
npm run typecheck              # React client only
npm test                       # epic / store unit tests
```

After installing:

1. Give access admins `x_1320561_itsm_ai.access_admin`, and give operations staff `x_1320561_itsm_ai.auditor`.
   Then grant per-API read/write to groups from the console (see *Admin workflow*).
2. Set `x_1320561_itsm_ai.known_error.kb` to the knowledge base for known-error drafts.
3. Set up the mesh's OAuth client on the instance with `servicenow/setup/oauth-mesh-client.js` (admin, per
   instance). Bind the APIs to the auth scope `x_1320561_itsm_ai.mesh_api`, which this app installs, and run
   `deploy/scripts/sn-oauth-smoke.sh`. See `docs/servicenow-oauth.md`. The OAuth registry and its secret are
   deliberately not part of the app.
4. Restrict REST access to the mesh's egress IPs.

`now.config.json` contains a newly generated `scopeId`. If the app already exists on your instance, set
`scopeId` to that application's sys_id before installing.

## Check against your release before go-live

`npm run build` verifies types and packaging only. Nothing here has been installed on an instance yet.
Before go-live, check these against your instance:

- **Change adapters** in `src/server/services/change-apis.ts`:
  - `sn_chg_rest.ChangeRequest.newNormal` / `newStandard`
  - `global.ChangeRequestStateHandler.isNext` / `moveTo`
  - `global.RiskCalculator`
  - `global.ChangeCheckConflicts`

  Each falls back to `GlideRecordSecure` or degrades gracefully. Run `npm run types` (`now-sdk dependencies`)
  to generate instance types and replace `types/platform-apis.d.ts`.
- **Choice values that differ per instance**:
  - incident `hold_reason` and `close_code`
  - change `close_code`
  - the problem state model (101–107)
  - RITM cancel (`state=4`, `stage=Request Cancelled`)
- **Catalog APIs**: `sn_sc.CatItem.canView()` and `sn_sc.CartJS.orderNow()`.
- **Writes to global user tables**: the access service writes to `sys_group_has_role`, `sys_user_grmember`
  and `sys_user_group` through cross-scope privileges. If your instance's Application Access settings on those
  tables block writes from other scopes, grant/membership calls return a clear 422. Either allow it, or give
  access admins `user_admin`.
- **Role changes and sessions**: role changes apply to new sessions. REST calls with OAuth tokens pick them up
  immediately; an interactive user may need to sign in again.
- **Route precedence**: literal paths such as `/similar` and `/templates` must take priority over `/{id}`.
  The URIs in the built `sys_ws_operation` records match what the MCP servers call.
