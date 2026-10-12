#!/usr/bin/env bash
# Deploys the demo stack to a VM over SSH (run from your workstation, in the repo root):
#   deploy/scripts/deploy-demo.sh agentruntime
# 1. syncs the repo to ~/itsm-agent-mesh on the server (secrets and .env on the server are never overwritten)
# 2. creates the server-side secrets on first run (the OAuth private key is generated ON the server)
# 3. stops with instructions until the ServiceNow client secret, Anthropic key and deploy/.env are in place
# 4. builds the images (Java build runs once, then is reused) and starts the stack
set -euo pipefail
HOST="${1:?usage: $0 <ssh host>}"
REMOTE_DIR="${REMOTE_DIR:-itsm-agent-mesh}"
cd "$(dirname "$0")/../.."

echo "==> syncing to $HOST:~/$REMOTE_DIR"
rsync -az --delete \
  --exclude .git --exclude '**/target' --exclude '**/node_modules' --exclude '**/.venv' --exclude '**/__pycache__' \
  --exclude 'servicenow/' --exclude '*.pdf' --exclude '**/*.db' \
  --exclude 'deploy/secrets/' --exclude 'deploy/.env' \
  ./ "$HOST:$REMOTE_DIR/"

ssh "$HOST" "REMOTE_DIR=$REMOTE_DIR HOST_ALIAS=$HOST bash -s" <<'REMOTE'
set -euo pipefail
cd ~/"$REMOTE_DIR"
command -v docker >/dev/null && docker compose version >/dev/null 2>&1 || {
  echo "Docker is not set up. Run once on the server:  sudo bash ~/$REMOTE_DIR/deploy/scripts/server-setup.sh"; exit 1; }

echo "==> secrets"
deploy/scripts/init-secrets.sh | sed 's/^/    /'
missing=""
for f in sn_oauth_client_secret anthropic_api_key; do [ -s deploy/secrets/$f ] || missing="$missing $f"; done
[ -f deploy/.env ] || missing="$missing deploy/.env"
if [ -n "$missing" ]; then
  cat <<MSG

Not deployed yet - missing:$missing
  1. Certificate to register in ServiceNow (paste into servicenow/setup/oauth-mesh-client.js):
       ssh $HOST_ALIAS cat $REMOTE_DIR/deploy/secrets/sn_oauth_certificate.pem
  2. Put the client secret printed by that script into  ~/$REMOTE_DIR/deploy/secrets/sn_oauth_client_secret
  3. Put your Anthropic API key into                     ~/$REMOTE_DIR/deploy/secrets/anthropic_api_key
  4. Create ~/$REMOTE_DIR/deploy/.env with SN_INSTANCE_URL, SN_OAUTH_CLIENT_ID, SN_OAUTH_KEY_ID
     (plus PUBLIC_HOST and ACME_EMAIL for setup-nginx.sh; see docs/deploy-demo.md and docs/servicenow-assistant.md)
  Then run the deploy again.
MSG
  exit 2
fi

compose="docker compose -f deploy/docker-compose.demo.yml --env-file deploy/.env"
echo "==> building (the first build takes several minutes)"
$compose build orchestrator          # compiles all Java modules once; the other services reuse that stage
$compose build
echo "==> starting"
$compose up -d --remove-orphans
echo "==> waiting for the services to settle"
sleep 45
$compose ps -a --format 'table {{.Service}}\t{{.State}}\t{{.Status}}'
bad=$($compose ps -a --format '{{.Service}} {{.State}}' | awk '$2 != "running" {print $1}')
for s in $bad; do echo "---- $s is not running; last log lines:"; $compose logs --tail 15 "$s" 2>&1 | grep -v '^\s*$' | tail -15; done
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}' | sed 's/deploy-//'
[ -z "$bad" ] || { echo "Some services are not running (see above)."; exit 3; }
REMOTE

cat <<MSG

HTTPS for ServiceNow (once): ssh $HOST 'sudo bash $REMOTE_DIR/deploy/scripts/setup-nginx.sh'
ServiceNow Assistant: set x_1320561_itsm_ai.mesh.endpoint = https://<PUBLIC_HOST> and
x_1320561_itsm_ai.mesh.channel_secret = the contents of deploy/secrets/channel_secret on the server,
then open "ITSM AI Agent > Assistant" in ServiceNow.

Developer chat (optional) through an SSH tunnel (the API only listens on the server's loopback):
  ssh -N -L 8080:localhost:8080 $HOST
  then browse http://localhost:8080 and enter a ServiceNow user_name as "Dev user".
Logs:  ssh $HOST "cd $REMOTE_DIR && docker compose -f deploy/docker-compose.demo.yml --env-file deploy/.env logs -f orchestrator agent-runtime"
MSG
