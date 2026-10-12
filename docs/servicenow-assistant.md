# The in-app Assistant: chat inside ServiceNow, agents on AWS

Users chat on the app's **Assistant** page in ServiceNow. Their messages go to the agent mesh on AWS, and the
mesh's agents work in ServiceNow on their behalf. The reply, and any change awaiting Confirm, appears in the
same chat.

```
 ServiceNow (PDI)                                                     AWS VM (Docker)
 ┌──────────────────────────────────────────────┐                     ┌──────────────────────────────────────────┐
 │ Assistant page  /x_1320561_itsm_ai_assistant.do │                     │ nginx on the host :443 (Let's Encrypt)   │
 │   React + Redux epics (send, poll, confirm)   │                     │   only /channel/servicenow/*              │
 │        │ same-origin, the user's own session  │                     │        ▼                                  │
 │        ▼                                      │  HTTPS, HMAC-signed │ Orchestrator  ── signature check          │
 │ Scripted REST /api/x_1320561_itsm_ai/v1/chat  ├────────────────────►│   screening, PII redaction, rate limit    │
 │   user = gs.getUserName()                     │  (user, ts, nonce)  │   background turn, polled by ServiceNow   │
 │   signs with x_..._mesh.channel_secret        │                     │        ▼                                  │
 └──────────────────────────────────────────────┘                     │ LangGraph runtime: coordinator, Access    │
            ▲                                                          │ agent, domain agents, Claude              │
            │  per-user OAuth token (signed assertion) + REST calls    │        ▼                                  │
            └──────────────────────────────────────────────────────────┤ 6 MCP servers ─► app REST APIs as the user│
                                                                       └──────────────────────────────────────────┘
```

- **The browser only talks to ServiceNow.** The user is whoever is signed in (`gs.getUserName()`). The browser
  can't claim to be someone else, there's no CORS setup, and no secret ever reaches the browser.
- **Every ServiceNow → AWS call is signed.** It carries an HMAC-SHA256 over timestamp, nonce, method, path, user
  and body, with a secret only the instance and the mesh hold. The mesh rejects unsigned, altered, stale (more
  than 5 minutes old) or replayed requests.
- **ServiceNow transactions stay short.** An agent turn can take a minute, so the instance starts it, gets an
  id back immediately, and the page polls every 1.5 s through the same signed channel.
- **The agents act as the user.** The mesh gets that user's own OAuth token (see
  [`servicenow-oauth.md`](servicenow-oauth.md)). The Access agent and the per-API read/write roles decide what
  they may do, and changes need the user's Confirm in the chat.
- **nginx exposes only the signed channel.** It runs on the EC2 host, forwards `/channel/servicenow/*` to
  the orchestrator on `127.0.0.1:8080`, and returns 404 for everything else, so the developer chat UI and `/api`
  stay private.

## What the chat shows

- **Replies are Markdown, rendered as HTML.** Lists, tables, code and links render properly. Replies can quote
  record text anyone may have written, so the HTML is sanitized: no scripts, event handlers or images. Links are
  limited to http(s), mailto and this instance, and open in a new tab.
- **How the agents worked.** While a turn runs, the chat lists its steps as they happen. After the reply, they
  stay under a collapsed *How I worked on this* line above it. The steps are:
  - which agent the coordinator chose;
  - the Access agent's read/write check;
  - the model's summarized thinking;
  - each MCP tool call, with its status and duration;
  - hand-offs between agents;
  - fact-check retries.
  Tool arguments and results are not shown.
- **Turning thinking off.** Thinking is on for the domain models (`thinking: true` in their model profiles). To
  turn it off, set `MESH_DOMAIN__THINKING=false` and `MESH_RISK__THINKING=false` on `agent-runtime`.

## Setup

Prerequisites:
- the VM is deployed per [`deploy-demo.md`](deploy-demo.md);
- OAuth is set up per [`servicenow-oauth.md`](servicenow-oauth.md);
- the latest app is installed (`npm run deploy`).

### 1. Give the VM a stable public HTTPS name

1. **Elastic IP.** In EC2, go to *Elastic IPs*, choose *Allocate*, then *Associate* it with the instance. A
   plain public IP changes on every stop/start, for example when resizing.
2. **Hostname.** Without a domain, use `sslip.io`, which resolves `<ip-with-dashes>.sslip.io` to that IP; for
   example `18-220-247-240.sslip.io`. With a domain, create an A record instead.
3. **Security group.** Allow inbound **TCP 80 and 443 from 0.0.0.0/0** (80 is only used by Let's Encrypt to
   issue the certificate), plus SSH. Leave 8080 closed.
4. **Settings.** Add to `deploy/.env` on the VM:
   ```
   PUBLIC_HOST=100-55-159-214.sslip.io
   ACME_EMAIL=you@example.com
   ```
5. **Set up nginx** (once):
   ```bash
   ssh apptronicsagent 'sudo bash ~/itsm-agent-mesh/deploy/scripts/setup-nginx.sh'
   ```
   The script:
   - installs nginx and certbot if they're missing;
   - checks that `PUBLIC_HOST` points at the instance;
   - gets a Let's Encrypt certificate and installs the proxy config (`deploy/nginx/`);
   - turns on automatic renewal (the `certbot-renew` timer, which reloads nginx);
   - checks the proxy. It reports **401** once the orchestrator is running (an unsigned test request is
     correctly rejected) and **502** before the stack is up.

   `https://<PUBLIC_HOST>/` itself answers 404; that's expected.

### 2. Point ServiceNow at the mesh (admin, per instance)

In ServiceNow, open `sys_properties.list` and set:

| Property | Value |
|---|---|
| `x_1320561_itsm_ai.mesh.endpoint` | `https://<PUBLIC_HOST>` (no trailing slash) |
| `x_1320561_itsm_ai.mesh.channel_secret` | output of `ssh agentruntime cat itsm-agent-mesh/deploy/secrets/channel_secret` |

The secret property is encrypted (`password2`) and only admins can read it. Neither value is part of the app,
because they differ per instance.

### 3. Let people use it

1. Give users `x_1320561_itsm_ai.chat_user`, which lets them open the Assistant (`agent_user` includes it).
2. Grant read/write per API to their groups in the console's **API access** view. The chat can only do what
   those grants allow.
3. Users open **ITSM AI Agent → Assistant** (`/x_1320561_itsm_ai_assistant.do`).

## Check it end to end

1. **Message:** in the Assistant, send "Show my open incidents". You get a reply from the Incident agent.
2. **Mesh logs:**
   - `docker compose ... logs orchestrator`: no `ServiceNow channel request rejected`.
   - `docker compose ... logs agent-runtime`: shows the turn.
3. **Audit:** the console's **Tool call audit** shows the agent's calls (api `incident`), and the `chat` calls,
   under the same user.
4. **Write:** try a change, e.g. "Add a work note to INC0010001: called the user". A preview card appears; nothing
   changes until you click **Confirm**.

| What you see | Cause |
|---|---|
| "The Assistant is not configured yet." | The endpoint property is empty or not `https://`, or the secret is empty |
| "The Assistant service is not reachable right now." | Wrong `PUBLIC_HOST`/DNS, security group missing 443, nginx not set up or no certificate (`sudo nginx -t`, `/var/log/nginx/itsm-mesh.error.log`), orchestrator down (nginx returns 502), or the VM is stopped |
| "The Assistant service rejected this instance." | The channel secret differs between ServiceNow and the VM, or the VM's clock is off by more than 5 minutes |
| "ServiceNow did not accept your account for the assistant." | OAuth: the user isn't matched by the registry's user field, or is inactive. See `servicenow-oauth.md` |
| "You don't have access to the … API" | Working as designed: grant the user's group read or write on that API |
