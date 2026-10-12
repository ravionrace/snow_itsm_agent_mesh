#!/usr/bin/env bash
# Puts nginx on the EC2 host in front of the Java orchestrator, with a Let's Encrypt certificate. Run on the server:
#   sudo bash ~/itsm-agent-mesh/deploy/scripts/setup-nginx.sh [PUBLIC_HOST] [EMAIL]
# Without arguments, PUBLIC_HOST and ACME_EMAIL are read from deploy/.env. Safe to re-run.
# Prerequisites: ports 80 and 443 open in the security group; PUBLIC_HOST resolves to this instance
# (an Elastic IP + <ip-with-dashes>.sslip.io works without owning a domain).
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
cd "$(dirname "$0")/.."

if [ -f .env ]; then set -a; . ./.env; set +a; fi
HOST="${1:-${PUBLIC_HOST:-}}"
EMAIL="${2:-${ACME_EMAIL:-}}"
[ -n "$HOST" ] && [ -n "$EMAIL" ] || { echo "usage: $0 <public host> <email>  (or set PUBLIC_HOST and ACME_EMAIL in deploy/.env)"; exit 1; }
[[ "$HOST" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "invalid host: $HOST"; exit 1; }

render() { sed "s/__PUBLIC_HOST__/$HOST/g" "nginx/$1" > "/etc/nginx/conf.d/$2"; }

echo "==> packages"
rpm -q nginx >/dev/null 2>&1 || dnf install -y -q nginx
rpm -q certbot >/dev/null 2>&1 || dnf install -y -q certbot
mkdir -p /var/www/certbot

echo "==> DNS check"
myip=$(curl -s -m 3 https://checkip.amazonaws.com || true)
dnsip=$(getent hosts "$HOST" | awk '{print $1}' | head -1 || true)
echo "    $HOST -> ${dnsip:-?}   this instance -> ${myip:-?}"
[ -n "$dnsip" ] && [ "$dnsip" = "$myip" ] || echo "    WARNING: $HOST does not resolve to this instance; certificate issuance will fail."

echo "==> HTTP (port 80) for the certificate challenge"
rm -f /etc/nginx/conf.d/itsm-mesh-https.conf
render itsm-mesh-http.conf itsm-mesh-http.conf
nginx -t -q
systemctl enable --now nginx
systemctl reload nginx

echo "==> certificate"
if [ ! -s "/etc/letsencrypt/live/$HOST/fullchain.pem" ]; then
  certbot certonly --webroot -w /var/www/certbot -d "$HOST" -m "$EMAIL" --agree-tos -n
else
  echo "    already present ($(openssl x509 -enddate -noout -in /etc/letsencrypt/live/$HOST/fullchain.pem))"
fi

echo "==> HTTPS (port 443) -> orchestrator 127.0.0.1:8080, /channel/servicenow/* only"
render itsm-mesh-https.conf itsm-mesh-https.conf
nginx -t -q
systemctl reload nginx

echo "==> automatic renewal"
mkdir -p /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\nsystemctl reload nginx\n' > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
systemctl enable --now certbot-renew.timer

echo "==> check"
code=$(curl -s -o /dev/null -w '%{http_code}' --resolve "$HOST:443:127.0.0.1" "https://$HOST/channel/servicenow/v1/turns/check" || true)
case "$code" in
  401) echo "    OK: nginx reaches the orchestrator (unsigned request correctly rejected with 401)";;
  502|504) echo "    nginx is up, but the orchestrator is not running yet (HTTP $code). Start the stack with deploy-demo.sh.";;
  *) echo "    unexpected HTTP $code - see /var/log/nginx/itsm-mesh.error.log";;
esac
echo "Done. ServiceNow property x_1320561_itsm_ai.mesh.endpoint = https://$HOST"
