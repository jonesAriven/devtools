#!/bin/bash
# ============================================================
# env.sh — 公共变量定义
# 被 build-*.sh (CI容器内) 和 deploy-*.sh (目标服务器) 共同 source
# ============================================================

# ====== 路径 ======
readonly SHARED_DIR="/mnt/shared/woodScript/publish"
readonly CI_DIR="/mnt/shared/woodScript/cd"
readonly DEPLOY_BASE="/root/kb-deploy"
readonly GIT_REPO="/root/devtools"

# ====== Nexus ======
readonly NEXUS_NPM_REGISTRY="http://192.168.31.105:8081/repository/npm-public/"

# ====== 健康检查 ======
readonly HEALTH_MAX_RETRIES=24
readonly HEALTH_INTERVAL=10

# ====== 部署服务器 ======
readonly HOST_MYKNG="192.168.31.105"
readonly HOST_DEBIAN="192.168.31.182"
