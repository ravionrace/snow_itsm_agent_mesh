#!/usr/bin/env bash
# One-time preparation of an Amazon Linux 2023 VM for the agent mesh demo. Run on the server:  sudo bash server-setup.sh
#  - checks the instance is big enough (>= ~3.5 GB RAM, >= 15 GB disk)
#  - grows the root filesystem if the EBS volume was enlarged
#  - installs Docker Engine (Amazon Linux package) + the Compose and Buildx CLI plugins (GitHub releases)
#  - adds a 2 GB swap file as a safety net for image builds
#  - lets ec2-user run docker without sudo
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
TARGET_USER="${SUDO_USER:-ec2-user}"

mem_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
echo "memory: ${mem_mb} MB"
if [ "$mem_mb" -lt 3500 ] && [ "${FORCE:-0}" != 1 ]; then
  echo "Too little memory for 7 Java services + the Python runtime (~2 GB) plus image builds."
  echo "Resize the instance to t3.medium (4 GB) or larger, or re-run with FORCE=1 to continue anyway."
  exit 1
fi

# Grow the root partition + XFS filesystem if the volume was enlarged (no-op otherwise).
root_dev=$(findmnt -n -o SOURCE /)
disk="/dev/$(lsblk -no PKNAME "$root_dev")"; part="${root_dev##*[!0-9]}"
growpart "$disk" "$part" 2>/dev/null || true
xfs_growfs -d / >/dev/null 2>&1 || true
disk_gb=$(df -BG --output=size / | tail -1 | tr -dc 0-9)
echo "root disk: ${disk_gb} GB"
[ "$disk_gb" -ge 15 ] || echo "WARNING: increase the EBS volume to 20 GB+; images and build cache need ~6 GB."

dnf install -y -q docker git
systemctl enable --now docker
usermod -aG docker "$TARGET_USER"

plugins=/usr/local/lib/docker/cli-plugins
mkdir -p "$plugins"
arch=$(uname -m); case "$arch" in x86_64) a=amd64; ca=x86_64 ;; aarch64) a=arm64; ca=aarch64 ;; *) echo "unsupported arch $arch"; exit 1 ;; esac
if ! docker compose version >/dev/null 2>&1; then
  curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ca}" -o "$plugins/docker-compose"
  chmod +x "$plugins/docker-compose"
fi
# Amazon Linux's docker package ships an old buildx (0.12) in /usr/libexec; Compose needs 0.17+. A newer copy in
# /usr/local/lib/docker/cli-plugins takes precedence.
bx=$(docker buildx version 2>/dev/null | awk '{print $2}' | tr -d v)
if [ -z "$bx" ] || [ "$(printf '%s\n0.17.0\n' "$bx" | sort -V | head -1)" != "0.17.0" ]; then
  tag=$(curl -fsSI https://github.com/docker/buildx/releases/latest | awk -F/ 'tolower($1) ~ /^location:/ {print $NF}' | tr -d '\r')
  curl -fsSL "https://github.com/docker/buildx/releases/download/${tag}/buildx-${tag}.linux-${a}" -o "$plugins/docker-buildx"
  chmod +x "$plugins/docker-buildx"
fi

if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo "swap: 2 GB added"
fi

docker --version; docker compose version; docker buildx version
echo "Done. Log out and back in (or run 'newgrp docker') so $TARGET_USER can use docker without sudo."
