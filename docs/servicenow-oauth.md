# ServiceNow OAuth for the agent mesh — runbook

How the agent mesh authenticates to ServiceNow, what an admin sets up per instance, and how to verify,
rotate and revoke it.

## How it works

```
 user ──SSO──► Orchestrator API ──(1) signed JWT assertion: iss=client_id, sub=<user>, kid, 60 s, jti──► ServiceNow /oauth_token.do
                     │                                                                                    │ verifies signature (verifier map),
                     │◄──────────────────────────(2) access token FOR THAT USER (30 min) ─────────────────┘ client secret, user, jti, scope
                     ▼
               agent runtime ──► MCP servers ──(3) Authorization: Bearer <user token>──► /api/x_1320561_itsm_ai/v1/...
```

- **The mesh is a confidential OAuth client.** It's registered in ServiceNow as an *OAuth JWT API endpoint
  for external clients*, with a client secret and the certificate of the mesh's own RSA signing key.
- **Tokens are per user** (OAuth 2.0 JWT bearer grant, RFC 7523). ServiceNow issues each token for the user
  named in `sub`, so ACLs, catalog entitlements, the API read/write roles and the audit trail all apply to
  the real person. No user passwords or IdP tokens are involved.
- **Tokens are restricted.** The registry is *Securely scoped* to the auth scope
  `x_1320561_itsm_ai.mesh_api`. Its tokens can call this app's REST APIs and nothing else: no Table API, no
  other applications.
- **Only the orchestrator holds the credentials.** The private key and client secret are mounted secrets in
  the orchestrator. The MCP servers and runtime only ever see a user's short-lived token.
- **Tokens are cached per user** and replaced 5 minutes before they expire. Concurrent requests for the
  same user share one token request.

## Who maintains what

| Item | Where | Why |
|---|---|---|
| Per-API roles, REST ACLs, auth scope `x_1320561_itsm_ai.mesh_api` | **Scoped app** (source control, `npm run deploy`) | No secrets; identical on every instance |
| OAuth registry (client id + **secret**), signing certificate, JWT verifier map, iss claim check, scope mapping | **Admin, per instance**, via `servicenow/setup/oauth-mesh-client.js` | Credentials differ per instance and must never be in source or update sets |
| Binding the app's REST APIs to the auth scope | **Admin, per instance** (UI, one-time) | The platform generates these choice values |
| Private key, client secret, mesh secrets | **Secret store** (Vault / Kubernetes / Docker secrets), mounted as files | Never in env vars, images or logs |
| IP address access control | **Admin, per instance** | Network-level allow-list for the mesh's egress IPs |

The scoped app doesn't create the OAuth registry. If it did, every instance would share one client id and
secret, and the secret would end up in source control.

## First-time setup (per instance)

Prerequisites: the `x_1320561_itsm_ai` app is installed (`npm run deploy`), and you're an admin on the instance.

### 1. Create the mesh's key pair and secrets

```bash
SN_OAUTH_KEY_ID=mesh-2026-10 deploy/scripts/init-secrets.sh
```

This creates, under `deploy/secrets/`:

- `sn_oauth_private_key`: RSA 3072, PKCS#8. It stays in your secret store.
- `sn_oauth_certificate.pem`: the public half, which goes to ServiceNow.
- The random mesh secrets.

The key id (`kid`) names this key pair everywhere. Use a dated id, so rotations are easy to follow.

### 2. Register the client in ServiceNow

1. Open `servicenow/setup/oauth-mesh-client.js` and set `CONFIG.keyId` and `CONFIG.certificatePem` (the
   contents of `sn_oauth_certificate.pem`).
2. Set `CONFIG.userField` to the `sys_user` field that holds the value your SSO sends in
   `SN_OAUTH_SUBJECT_CLAIM`. Common pairs:
   - `user_name` ↔ `preferred_username` / `upn`
   - `email` ↔ `email`
3. In ServiceNow: **System Definition → Scripts - Background**, scope **global**. Paste the script and run it.
4. Copy the printed **Client id**. Store the printed **Client secret** (shown once) as
   `deploy/secrets/sn_oauth_client_secret`. Then clear the script output.

The script is idempotent. Re-running it updates the configuration without changing the secret, unless you
set `rotateSecret: true`.

### 3. Bind the app's REST APIs to the auth scope

The script ends with `ACTION NEEDED: bind these REST APIs ...` until this is done. Open
`sys_api_access_scope.list` and click **New**. For each API (`incident`, `problem`, `change`, `catalog`,
`cmdb`, `kb`, `access`), create one record:

| Field | Value |
|---|---|
| REST API | ITSM AI Agent - &lt;api&gt; |
| Auth Scope | `x_1320561_itsm_ai.mesh_api` |
| Apply auth scope to all resources / all methods / all versions | ✓ / ✓ / ✓ |
| Active | ✓ |

Re-run the setup script; it should report `All 7 REST APIs are bound`.

### 4. Restrict the network

Allow REST and `/oauth_token.do` only from the mesh's egress IPs: **System Security → IP Address Access
Control** (or Adaptive Authentication IP filters).

### 5. Verify before starting the mesh

```bash
SN_INSTANCE_URL=https://<instance>.service-now.com SN_OAUTH_CLIENT_ID=<client id> \
SN_OAUTH_CLIENT_SECRET_FILE=deploy/secrets/sn_oauth_client_secret \
SN_OAUTH_PRIVATE_KEY_FILE=deploy/secrets/sn_oauth_private_key SN_OAUTH_KEY_ID=mesh-2026-10 \
deploy/scripts/sn-oauth-smoke.sh <a test user's user_name>
```

The script passes only when all three hold:

- a token is issued for that user;
- `GET /access/me` answers with that user's permissions;
- the same token is **refused** by the Table API, which proves the scope restriction.

### 6. Configure and start the mesh

Copy `deploy/.env.example` to `deploy/.env` and set the instance URL, IdP and client id. Then:

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

With `ITSM_ENVIRONMENT=production`, every service refuses to start on unsafe settings and lists them all:
- DEV auth, the STATIC token mode, http URLs;
- missing or inline secrets, or the development confirmation secret;
- in-memory sessions or a SQLite database.

## Operations

| Task | How |
|---|---|
| **Rotate the signing key** (yearly, or on suspicion) | Generate a new key and certificate with a new `kid` (move the old `sn_oauth_private_key` aside, then run `init-secrets.sh` with the new `SN_OAUTH_KEY_ID`). Re-run the setup script with the new `keyId` and certificate; both keys are now accepted. Deploy the mesh with the new key and `SN_OAUTH_KEY_ID`. Then delete the old `jwt_verifier_map` row and its certificate. |
| **Rotate the client secret** | Re-run the setup script with `rotateSecret: true`, update the secret file, restart the orchestrator. The old secret stops working immediately, so do it in a maintenance window or with a rolling restart. |
| **Revoke the mesh immediately** | Set the OAuth registry `ITSM AI Agent Mesh` to inactive. Optionally delete its issued tokens: `oauth_credential` rows for that client. |
| **Remove one user's access** | Remove them from the groups holding `x_1320561_itsm_ai.*` roles (console → API access), or deactivate the user. ServiceNow then refuses to issue them tokens. |
| **Certificate expiry** | The certificate record has expiry notification on (30 days ahead). Rotate before it expires. |
| **Monitor** | Orchestrator metrics: `itsm_servicenow_token_mint_seconds`, `itsm_servicenow_token_issued_total`, `itsm_servicenow_token_failures_total{reason}` (rejected, unavailable, unreachable), `itsm_servicenow_token_cached_users`. Alert on a rising `failures{reason="rejected"}`, which usually means user-mapping or key problems. |

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Smoke test: `invalid_client` | Wrong client id or secret, or the registry is inactive |
| Smoke test: `access_denied` / user not found | `CONFIG.userField` doesn't match what the mesh sends as `sub`, or the user is inactive or locked |
| Signature / `invalid_grant` | `kid` mismatch between the mesh and the verifier map, the wrong certificate, or the key isn't PKCS#8 |
| `jti` already used | Clock skew or a replayed assertion. Check NTP on the orchestrator hosts. |
| `/access/me` returns 401 | The REST API isn't bound to the auth scope (step 3) |
| Table API returns 200 in the smoke test | The registry isn't *Securely scoped*, or is mapped to `useraccount` |
| Users get "ServiceNow did not accept your account for the assistant" | Token issuing was rejected for that user. Check the orchestrator log line `ServiceNow token request failed (rejected)`. |
