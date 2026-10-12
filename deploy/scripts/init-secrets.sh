#!/usr/bin/env bash
# Creates deploy/secrets/* for docker-compose (or as the source for Kubernetes / Vault secrets).
#  - random shared secrets (mesh key, confirmation secret, runtime key, session vault key, MySQL password)
#  - the mesh's OAuth signing key pair: sn_oauth_private_key (PKCS#8, stays here) and
#    sn_oauth_certificate.pem (PUBLIC, paste into servicenow/setup/oauth-mesh-client.js)
# Existing files are never overwritten. You add two values from ServiceNow / Anthropic afterwards:
#    sn_oauth_client_secret   printed by the ServiceNow setup script
#    anthropic_api_key        your Anthropic API key
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p secrets && chmod 700 secrets
umask 077

new() { [ -s "secrets/$1" ] && echo "keep    secrets/$1" || { printf '%s' "$2" > "secrets/$1"; echo "created secrets/$1"; }; }
rand() { openssl rand -base64 48 | tr -d '\n/+=' | cut -c1-48; }

new mesh_key "$(rand)"
new confirmation_secret "$(rand)"
new runtime_api_key "$(rand)"
new session_vault_key "$(openssl rand -base64 32)"
new channel_secret "$(openssl rand -base64 32)"   # also set in ServiceNow: x_1320561_itsm_ai.mesh.channel_secret
new mysql_password "$(rand)"
[ -s secrets/database_url ] || new database_url "mysql+pymysql://mesh:$(cat secrets/mysql_password)@mysql:3306/itsm_mesh"

if [ ! -s secrets/sn_oauth_private_key ]; then
  KID="${SN_OAUTH_KEY_ID:-mesh-$(date +%Y-%m)}"
  openssl genrsa -out secrets/.rsa.pem 3072 2>/dev/null
  openssl pkcs8 -topk8 -nocrypt -in secrets/.rsa.pem -out secrets/sn_oauth_private_key
  openssl req -new -x509 -key secrets/.rsa.pem -days 365 -subj "/CN=ITSM AI Agent Mesh ${KID}" \
    -out secrets/sn_oauth_certificate.pem 2>/dev/null
  rm -f secrets/.rsa.pem
  chmod 644 secrets/sn_oauth_certificate.pem
  echo "created secrets/sn_oauth_private_key (RSA 3072, PKCS#8) and secrets/sn_oauth_certificate.pem (valid 365 days)"
  echo "        key id to use everywhere: ${KID}   (SN_OAUTH_KEY_ID and CONFIG.keyId)"
else
  echo "keep    secrets/sn_oauth_private_key"
fi

# Compose bind-mounts file secrets with their host permissions, and the containers run as uid 10001, not as the
# file owner. The 700 directory keeps other host users out; the files themselves must be readable inside containers.
chmod 700 secrets
find secrets -maxdepth 1 -type f ! -name '.*' -exec chmod 644 {} +

for f in sn_oauth_client_secret anthropic_api_key; do
  [ -s "secrets/$f" ] || echo "TODO    secrets/$f  (see the comments at the top of this script)"
done
