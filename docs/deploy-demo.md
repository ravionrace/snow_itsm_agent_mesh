# Demo deployment: one AWS VM + a ServiceNow PDI

Everything runs as Docker containers on one VM. Only the scoped app runs inside ServiceNow.

```
 your laptop ──ssh tunnel :8080──► VM (Docker)
                                    orchestrator (Java) ─► agent-runtime (Python/LangGraph) ─► 6 MCP servers (Java)
                                                                                                  │ HTTPS, per-user OAuth tokens
                                                                                                  ▼
                                                                          ServiceNow PDI: x_1320561_itsm_ai app
```

| Container | Image | Memory (measured / limit) |
|---|---|---|
| `orchestrator` | Java 17 (Spring Boot) | ~160 MB / 384 MB |
| `mcp-incident`, `mcp-problem`, `mcp-change`, `mcp-catalog`, `mcp-cmdb-kb`, `mcp-access` | Java 17 (Spring AI MCP) | ~160 MB each / 384 MB |
| `agent-runtime` | Python 3.13 (LangGraph) | ~150–250 MB / 640 MB |

The total is about 2 GB, plus about 1 GB while images build.

## 1. Size the VM

| | Minimum for this demo |
|---|---|
| Instance type | **t3.medium** (2 vCPU, 4 GB). t3.large is comfortable. |
| Root volume | **20 GB** gp3 |
| Security group | Inbound **SSH (22) only**. Don't open 8080. |
| Outbound | HTTPS to `*.service-now.com`, `api.anthropic.com`, Docker Hub, Maven Central, PyPI, GitHub |

To resize an existing instance, use the EC2 console:
1. *Instance state → Stop*.
2. *Actions → Instance settings → Change instance type*.
3. *Volumes → Modify volume* to 20 GB.
4. *Start*.

`server-setup.sh` grows the filesystem if it hasn't grown on its own.

## 2. Prepare the VM (once)

```bash
deploy/scripts/deploy-demo.sh agentruntime     # first run only syncs the code and stops: Docker is missing
ssh agentruntime 'sudo bash ~/itsm-agent-mesh/deploy/scripts/server-setup.sh'
```

`server-setup.sh` does the following:
- checks memory and disk;
- installs Docker, plus the Compose and Buildx plugins;
- adds 2 GB of swap as a safety net for builds;
- lets `ec2-user` run docker.

## 3. Prepare the PDI (once)

1. Install the scoped app from your laptop: `cd servicenow/x_acme_itsm_ai && npm run build && npm run deploy`.
2. Wake the PDI if it's hibernating (developer.servicenow.com). PDIs sleep when idle and the mesh can't reach
   them then.
3. Run the deploy once more: `deploy/scripts/deploy-demo.sh agentruntime`. It generates the secrets **on the
   server**, including the OAuth key pair, so the private key never leaves the VM. It then stops and lists
   what's missing.
4. Print the certificate: `ssh agentruntime cat itsm-agent-mesh/deploy/secrets/sn_oauth_certificate.pem`.
5. Follow [`servicenow-oauth.md`](servicenow-oauth.md) steps 2–3:
   - run `servicenow/setup/oauth-mesh-client.js` with that certificate, `keyId` = the printed key id, and
     `userField: 'user_name'`;
   - bind the 7 REST APIs to the auth scope.
6. Grant access. Either give your demo user `x_1320561_itsm_ai.agent_user`, or use the console's **API access**
   view to grant read/write per API to a group and add the user to it. (The PDI `admin` user has every role
   already.)

## 4. Add the remaining settings on the VM

```bash
ssh agentruntime
cd itsm-agent-mesh/deploy
printf '%s' '<client secret printed by the setup script>' > secrets/sn_oauth_client_secret
printf '%s' '<your Anthropic API key>' > secrets/anthropic_api_key
cat > .env <<'ENV'
SN_INSTANCE_URL=https://dev387916.service-now.com
SN_APP_SCOPE=x_1320561_itsm_ai
SN_OAUTH_CLIENT_ID=<client id printed by the setup script>
SN_OAUTH_KEY_ID=<key id printed by init-secrets>
ENV
```

Optionally, check OAuth before starting anything:

```bash
cd ~/itsm-agent-mesh && SN_INSTANCE_URL=https://dev387916.service-now.com SN_OAUTH_CLIENT_ID=<id> \
  SN_OAUTH_CLIENT_SECRET_FILE=deploy/secrets/sn_oauth_client_secret \
  SN_OAUTH_PRIVATE_KEY_FILE=deploy/secrets/sn_oauth_private_key SN_OAUTH_KEY_ID=<kid> \
  deploy/scripts/sn-oauth-smoke.sh admin
```

## 5. Deploy and use

```bash
deploy/scripts/deploy-demo.sh agentruntime     # builds the images, starts the stack, prints memory per container
ssh -N -L 8080:localhost:8080 agentruntime     # keep this running
```

Open http://localhost:8080 and type a ServiceNow **user_name** (for example `admin`) in *Dev user*. Then try:

- "Show my open incidents"
- "What's the status of INC0010001?"
- "Order a new laptop"

Writes show a preview with Confirm and Reject buttons.

Re-run `deploy-demo.sh` after every code change; only changed images are rebuilt.

For the in-ServiceNow Assistant, also run `setup-nginx.sh` once. It puts HTTPS in front of the orchestrator;
see [`servicenow-assistant.md`](servicenow-assistant.md).

## Operations

```bash
ssh agentruntime
cd itsm-agent-mesh
alias dc='docker compose -f deploy/docker-compose.demo.yml --env-file deploy/.env'
dc ps                                   # status
dc logs -f orchestrator agent-runtime   # logs
docker stats --no-stream                # memory per container
dc restart agent-runtime                # restart one service
dc down                                 # stop everything (chat sessions are in memory and are lost)
```

## Demo vs production

| | Demo (`docker-compose.demo.yml`) | Production (`docker-compose.yml`) |
|---|---|---|
| Sign-in | DEV header, API on loopback behind an SSH tunnel | Corporate IdP (JWT), API behind TLS / load balancer |
| ServiceNow auth | Per-user OAuth tokens (signed assertion) | Same |
| Sessions / audit store | Memory / SQLite | MongoDB / MySQL |
| Start-up guard | Off (`development`) | On (`production`) |
