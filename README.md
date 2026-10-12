# ServiceNow ITSM Agent Mesh

An implementation of the reference architecture in `ServiceNow-ITSM-Agent-Mesh.pdf`. A coordinator routes each
request to one of four domain agents (Incident, Problem, Change, Catalog). Each agent owns exactly one MCP
server, plus shared CMDB & Knowledge reads. Each MCP server calls its own Scripted REST API in the scoped app
`x_1320561_itsm_ai`, always as the signed-in user.

```
 chat UI ──► Orchestrator API (Spring Boot) ──► Agent runtime (LangGraph) ──► MCP servers (Spring AI) ──► x_1320561_itsm_ai
             SSO · SN token · PII redaction      coordinator + 4 agents        one per domain            Scripted REST APIs
             input screening · rate limit        guardrails · budgets · evals  coded business rules      GlideRecordSecure · ACLs · audit
                                                 MongoDB checkpoints · MySQL
```

## Where each part of the design lives

| Design element | Implementation |
|---|---|
| User interface (chat) | `orchestrator/src/main/resources/static/index.html`, with preview cards and Confirm / Reject |
| Authentication (corporate IdP, OAuth 2.0) | `orchestrator/.../security/SecurityConfig.java` (JWT resource server) |
| Act as the user, not a service account | `ServiceNowTokenBroker.java` (passthrough or JWT-bearer exchange) → `X-SN-Token` → MCP `Authorization` → ServiceNow |
| Orchestrator API (Spring Boot) | `orchestrator/` |
| PII redaction before any prompt reaches an LLM | `guard/PiiRedactor.java` (reversible placeholders + vault); runtime rehydrates tool args only |
| Agent runtime: coordinator + domain agents | `agent-runtime/src/itsm_agents/graph.py` (LangGraph `StateGraph`), `agents.py` |
| Coordinator hand-offs (Incident→Problem, Problem→Change, Catalog→Incident) | `handoff` harness tool + `handoff` node; typed JSON in state, not in the prompt |
| LLM gateway (self-hosted / third-party) | `llm.py`: Claude via the official Anthropic SDK; self-hosted via an OpenAI-compatible endpoint |
| Session store (history, shared state) | LangGraph checkpoints in MongoDB (`runtime.py → MongoDBSaver`) |
| Cost tracker (tokens per agent / ticket) | `observability.py` → MySQL `cost_ledger` |
| Observability (one trace per request) | OpenTelemetry spans + full trace documents in MongoDB `traces`; audit summary in MySQL `tool_call_audit` |
| Agent evaluation (golden tickets, routing accuracy) | `itsm_agents/evals/` (`itsm-evals`), CI gate in `.github/workflows/evals.yml` |
| Incident / Problem / Change / Catalog / CMDB & KB MCP | `mcp-incident`, `mcp-problem`, `mcp-change`, `mcp-catalog`, `mcp-cmdb-kb` (one Spring Boot service each) |
| Per-user API permissions (read/write per API, granted to groups) | Scoped app roles + REST ACLs; managed in the console's **API access** view |
| Access MCP + Access agent | `mcp-access` (`get_my_permissions`, `check_permission`) and the `authorize` node in `graph.py` (`access.py`) |
| Scoped app: Scripted REST APIs, ACLs, audit table | `servicenow/x_acme_itsm_ai/` — a now-sdk (Fluent) project: `RestApi` + TypeScript server modules |
| Operations console (React) | `servicenow/x_acme_itsm_ai/src/client` — UI page with `@servicenow/react-components`, built with Vite |

## Guardrails (all enforced in code or by ServiceNow)

| # | Layer | Where | What |
|---|---|---|---|
| 1 | Input | Orchestrator API | `PiiRedactor`, `InputScreen` (injection), `UserRateLimiter`; off-topic is refused by the coordinator |
| 2 | Tool results | Runtime `guardrails.wrap_tool_result` | Results wrapped as untrusted data, embedded instructions flagged, secrets scrubbed |
| 3 | Tool access | Runtime `agents.py` + `check_tool_call` + Access agent | Each agent sees only its own server + CMDB/KB, and of those only the tools the user has read/write access to (from the Access MCP); refused early, re-checked before each call and on Confirm, fails closed |
| 4 | Tool arguments | MCP servers (`Args`, `*Rules`) | Schema/enum checks, no P1 resolve, own groups only, no emergency changes, change state order |
| 5 | Writes | MCP `ConfirmationService` + LangGraph `interrupt()` | Preview + HMAC confirmation id; commit only on the user's Confirm; id never reaches the model or UI |
| 6 | Platform | Scoped app | REST ACLs, GlideRecordSecure, business rules, audit, idempotency |
| 7 | Output | Runtime `output_guard` node | Record numbers, dates and states in a reply must come from a tool result or the user; one retry, then stripped |
| 8 | Budget | Runtime `Budget` | Caps on model calls, tool calls, tokens and seconds per request; one hand-off chain limit |

## How a turn flows

```
start_turn → route (claude-haiku-4-5, structured output)
          → authorize (Access agent: Access MCP → /access/me as the user; no read access → polite refusal)
          → agent_step (offered only the tools the user may use) (claude-opus-5-5; Change agent at effort high) ⇄ run_tools (MCP, as the user)
          → [handoff → agent_step]
          → output_guard (grounding) → deliver
          → await_confirmation  ── interrupt() ──  POST /confirmations/{id} resumes ──► commit with confirmation_id
```

Models default to Claude Opus 5.5 for the domain agents (effort `medium`, `high` for Change). Routing and
history summaries use Claude Haiku 4.5. All of these are set in `agent-runtime/src/itsm_agents/config.py` or with
`MESH_*` environment variables. Opus requests opt into server-side refusal fallbacks (`fallbacks: "default"`);
turn this off with `MESH_ANTHROPIC_FALLBACKS=false`. To use a self-hosted model, set a profile to
`provider: openai_compatible` with its `base_url`.

## Layout

```
pom.xml                     Maven parent (Spring Boot 3.5, Spring AI 1.1.6, Java 17)
mcp-common/                 ServiceNow client, two-step confirmations, arg validation, rate limit, MCP auth context
mcp-{incident,problem,change,catalog,cmdb-kb}/   MCP servers (stateless streamable HTTP at /mcp)
mcp-access/                 Access MCP server: the signed-in user's read/write permissions per API
orchestrator/               Edge API + chat UI
agent-runtime/              LangGraph runtime (Python 3.11+), evals, tests
servicenow/x_acme_itsm_ai/  Scoped app (now-sdk): REST layer + React console; npm run build / deploy
deploy/                     Dockerfiles, docker-compose, .env.example
```

## Chat inside ServiceNow

Users chat on the app's **Assistant** page (`/x_1320561_itsm_ai_assistant.do`). ServiceNow relays each message
to the mesh on AWS through a signed server-to-server channel, and the agents work as that user. See
[`docs/servicenow-assistant.md`](docs/servicenow-assistant.md).

## Demo on one VM

[`docs/deploy-demo.md`](docs/deploy-demo.md) runs the whole mesh as Docker containers on one VM (t3.medium or
larger) against a ServiceNow PDI: `deploy/scripts/deploy-demo.sh <ssh-host>` with
`deploy/docker-compose.demo.yml`.

## Production

- **ServiceNow OAuth.** The mesh's OAuth client, signing certificate and auth-scope restriction are set up
  per instance by an admin, using `servicenow/setup/oauth-mesh-client.js`. They are not part of the scoped
  app, because they are credentials. `deploy/scripts/sn-oauth-smoke.sh` proves three things: tokens are
  issued per user, they reach the app's APIs, and they can't reach anything else. See
  [`docs/servicenow-oauth.md`](docs/servicenow-oauth.md), which also covers rotation, revocation,
  monitoring and troubleshooting.
- **Secrets are files, never environment variables.** `deploy/scripts/init-secrets.sh` creates them, and
  `deploy/docker-compose.yml` mounts them as Docker secrets. On Kubernetes, use Secret volumes at the same
  paths.
- **`ITSM_ENVIRONMENT=production` puts every service behind a start-up guard.** The orchestrator, MCP
  servers and runtime refuse development-only settings and list every problem at once:
  - DEV auth or STATIC tokens;
  - http URLs;
  - missing or inline secrets, or the development confirmation secret;
  - in-memory sessions or SQLite.
- **Only the orchestrator is exposed.** MCP servers accept calls only with the mesh key, and the runtime
  only with the runtime key.

## Run it

```bash
# Java: build and unit-test MCP servers + orchestrator
mvn verify

# Runtime
cd agent-runtime && python3.13 -m venv .venv && .venv/bin/pip install -e ".[dev]"
.venv/bin/pytest -q                                              # unit + graph tests (scripted model)
(cd .. && mvn -q -DskipTests package) && MESH_IT=1 .venv/bin/pytest tests/integration -q   # real MCP servers, mock ServiceNow

# Whole stack
cp deploy/.env.example deploy/.env    # fill in
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
# open http://localhost:8080
```

For local work without an IdP, set `AUTH_MODE=DEV`, `SN_TOKEN_MODE=STATIC` and `SN_STATIC_TOKEN` (for a personal
developer instance), and `SESSION_STORE=memory`. The UI's "Dev user" field is then sent as `X-Dev-User`. Never
enable DEV mode outside a developer machine.

## Evals

```bash
cd agent-runtime
itsm-evals --routing-only                 # coordinator only, cheapest
itsm-evals --suite all --report out.json  # golden + adversarial replay, real models, fixture MCP (no writes)
itsm-evals build-golden --csv closed_incidents.csv --out my_golden.jsonl   # from a closed-ticket export
itsm-evals --cases my_golden.jsonl --baseline evals_baseline.json --update-baseline
```

Scores: routing accuracy, tool choice, assignment-group match against what humans chose, and safety
(adversarial). The thresholds in `evals/data/baseline.json` are starting targets, not measured numbers. Run
the suite once against your models and record real baselines with `--update-baseline`. Every eval run spends
model tokens.

## Status

- Verified here: 22 Java tests, 20 runtime tests, and 3 integration tests. The integration tests run the
  LangGraph runtime against the real Java MCP servers and a mock ServiceNow, covering preview, confirm,
  idempotent commit, the P1 block and token rejection.
- Not yet run: anything against a live ServiceNow instance or live Claude models. Before go-live, check the
  release-dependent change, catalog and choice-value APIs listed in `servicenow/x_acme_itsm_ai/README.md`
  (the app builds with `npm run build`, but has not been installed on an instance), and
  run the eval suite to set real baselines.
