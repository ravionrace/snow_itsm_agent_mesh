#!/usr/bin/env bash
# Verifies a ServiceNow instance's OAuth setup for the ITSM AI agent mesh, without running the mesh:
#   1. signs a JWT assertion for a test user with the mesh's private key (RS256, kid, 60 s, jti)
#   2. exchanges it at /oauth_token.do (JWT bearer grant) for that user's token
#   3. calls GET /api/<scope>/v1/access/me with the token        -> expect 200 and the user's permissions
#   4. calls the Table API with the same token                    -> expect 401/403 (token is scope-restricted)
#
# Usage:
#   SN_INSTANCE_URL=https://acme.service-now.com SN_OAUTH_CLIENT_ID=... SN_OAUTH_CLIENT_SECRET_FILE=./secret \
#   SN_OAUTH_PRIVATE_KEY_FILE=./mesh.key.pem SN_OAUTH_KEY_ID=mesh-2026-10 \
#   deploy/scripts/sn-oauth-smoke.sh <user value matching the registry's user field>
set -euo pipefail

SUBJECT="${1:?usage: $0 <user (e.g. user_name or email)>}"
: "${SN_INSTANCE_URL:?}" "${SN_OAUTH_CLIENT_ID:?}" "${SN_OAUTH_CLIENT_SECRET_FILE:?}" "${SN_OAUTH_PRIVATE_KEY_FILE:?}" "${SN_OAUTH_KEY_ID:?}"
SCOPE="${SN_APP_SCOPE:-x_1320561_itsm_ai}"
BASE="${SN_INSTANCE_URL%/}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
now=$(date +%s)
header=$(printf '{"alg":"RS256","typ":"JWT","kid":"%s"}' "$SN_OAUTH_KEY_ID" | b64url)
claims=$(printf '{"iss":"%s","sub":"%s","aud":"%s","iat":%d,"nbf":%d,"exp":%d,"jti":"%s"}' \
  "$SN_OAUTH_CLIENT_ID" "$SUBJECT" "$SN_OAUTH_CLIENT_ID" "$now" "$((now - 5))" "$((now + 60))" "$(openssl rand -hex 16)" | b64url)
signature=$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign "$SN_OAUTH_PRIVATE_KEY_FILE" | b64url)
assertion="$header.$claims.$signature"

echo "1/3 token for '$SUBJECT' ..."
token_json=$(curl -sS -X POST "$BASE/oauth_token.do" \
  --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  --data-urlencode "client_id=$SN_OAUTH_CLIENT_ID" \
  --data-urlencode "client_secret@$SN_OAUTH_CLIENT_SECRET_FILE" \
  --data-urlencode "assertion=$assertion")
token=$(printf '%s' "$token_json" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$token" ]; then
  echo "FAIL: no token. ServiceNow said: $token_json" >&2
  echo "Check: client id/secret, kid and certificate in the verifier map, the user exists and is active," >&2
  echo "       and the registry's user field matches '$SUBJECT'." >&2
  exit 1
fi
echo "     ok (expires_in $(printf '%s' "$token_json" | sed -n 's/.*"expires_in":\([0-9]*\).*/\1/p') s)"

echo "2/3 GET /api/$SCOPE/v1/access/me as '$SUBJECT' ..."
code=$(curl -sS -o /tmp/sn-smoke-me.json -w '%{http_code}' -H "Authorization: Bearer $token" -H 'Accept: application/json' \
  "$BASE/api/$SCOPE/v1/access/me")
if [ "$code" != "200" ]; then
  echo "FAIL: HTTP $code $(cat /tmp/sn-smoke-me.json)" >&2
  echo "Check: the app's REST APIs are bound to auth scope $SCOPE.mesh_api (REST API Auth Scopes)." >&2
  exit 1
fi
echo "     ok: $(head -c 300 /tmp/sn-smoke-me.json)..."

echo "3/3 Table API with the same token must be refused (scope restriction) ..."
code=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" "$BASE/api/now/table/sys_user?sysparm_limit=1")
if [ "$code" = "200" ]; then
  echo "FAIL: the token can call the Table API. Set the registry to 'Securely scoped' and map only $SCOPE.mesh_api." >&2
  exit 1
fi
echo "     ok: HTTP $code"
rm -f /tmp/sn-smoke-me.json
echo "PASS: the mesh's OAuth client works for '$SUBJECT' and its tokens are limited to the app's APIs."
