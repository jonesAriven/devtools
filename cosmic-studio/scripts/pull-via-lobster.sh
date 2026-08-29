#!/bin/bash
# pull-via-lobster.sh — 经龙虾(海外机)中转拉取 docker 镜像到本机(mykng)
# 背景：docker.io 域名在国内被 DNS 污染（registry-1.docker.io 解析到假 IP），
#       Nexus docker-hub-direct 上游基本不可用；龙虾海外网络拉取正常。
# 用法: ./pull-via-lobster.sh <image> [image...]
#       例: ./pull-via-lobster.sh python:3.12-slim nginx:1.27
# 依赖: mykng → 龙虾 SSH 免密（已验证）；龙虾有 docker
set -e
LOBSTER="root@100.122.231.95"

[ $# -eq 0 ] && { echo "用法: $0 <image> [image...]"; exit 1; }
echo "==> 龙虾预检"
ssh -o BatchMode=yes -o ConnectTimeout=8 "$LOBSTER" "docker --version >/dev/null" || { echo "❌ 龙虾 docker/SSH 不可达"; exit 1; }

for IMG in "$@"; do
  echo "==> [$IMG] 龙虾拉取（海外直连 docker.io）"
  ssh -o BatchMode=yes "$LOBSTER" "docker pull -q '$IMG'"
  echo "==> [$IMG] 传输回 mykng（Tailscale 局域网）"
  ssh -o BatchMode=yes "$LOBSTER" "docker save '$IMG'" | docker load
  echo "==> [$IMG] 清理龙虾侧镜像"
  ssh -o BatchMode=yes "$LOBSTER" "docker rmi '$IMG' >/dev/null 2>&1 || true"
  echo "✅ [$IMG] 完成: $(docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' "$IMG" | head -1)"
done
