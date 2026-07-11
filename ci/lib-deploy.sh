#!/bin/bash
# ============================================================
# lib-deploy.sh — 标准化部署公共函数库
# ============================================================
# 被所有 deploy-*.sh 通过 source 引入，提供统一的部署原语。
# 设计原则:
#   1. 每个函数只做一件事
#   2. 函数间无隐式依赖，参数全部显式传递
#   3. 所有操作有日志输出
# ============================================================

# ====== 颜色 ======
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ====== 常量 ======
readonly SHARED_DIR="/mnt/shared/devtools/publish"
readonly CI_DIR="/mnt/shared/devtools/ci"
readonly DEPLOY_BASE="/root/kb-deploy"
readonly GIT_REPO="/root/devtools"
readonly HEALTH_MAX_RETRIES=24
readonly HEALTH_INTERVAL=10

# ====== 日志函数 ======
log_header() {
  echo ""
  echo "============================================="
  echo "  $1 — 自动部署"
  echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "  产物: $2"
  echo "============================================="
}

log_footer() {
  echo ""
  echo "============================================="
  echo "  $1 部署完成!"
  echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "  产物: $2"
  ${3:+echo "$3"}
  echo "============================================="
}

log_step() { echo ""; echo ">>> [$1/$2] $3 <<<"; }
log_ok()   { echo -e "  ${GREEN}✅${NC} $1"; }
log_warn() { echo -e "  ${YELLOW}⚠️${NC} $1"; }
log_err()  { echo -e "  ${RED}❌${NC} $1"; }
log_info() { echo -e "  ${BLUE}ℹ️${NC} $1"; }

# ====== 心跳函数 (防SSH超时断连) ======
_heartbeat_start() {
  _HB_MSG="${1:-操作进行中}"
  while true; do
    echo "  ⏳ $(date '+%H:%M:%S') ${_HB_MSG}..."
    sleep 15
  done
}

_heartbeat_stop() {
  kill ${_HB_PID} 2>/dev/null; wait ${_HB_PID} 2>/dev/null
}

# ====== 验证产物 ======
# 用法: verify_artifact <tar文件名>
# 输出: 返回完整路径到 stdout
verify_artifact() {
  local tar_file="$1"
  local tar_path="${SHARED_DIR}/${tar_file}"

  if [ ! -f "${tar_path}" ]; then
    log_err "产物不存在: ${tar_path}"
    log_info "请确认 CI Build 步骤已成功将产物推送到共享目录"
    exit 1
  fi
  local size=$(ls -lh "${tar_path}" | awk '{print $5}')
  log_ok "找到产物: ${tar_file} (${size})"
}

# ====== 解压产物 ======
# 用法: extract_artifact <tar文件名> <目标目录>
# 说明: tar 解压自动覆盖同名文件，无需 rm
extract_artifact() {
  local tar_file="$1"
  local target_dir="$2"
  local tar_path="${SHARED_DIR}/${tar_file}"

  mkdir -p "${target_dir}"
  tar xzf "${tar_path}" -C "${target_dir}"
  local count=$(find "${target_dir}" -maxdepth 1 -type f | wc -l)
  log_ok "解压到 ${target_dir}: ${count} 个文件"
}

# ====== 同步 compose 文件到部署目录 ======
# 用法: sync_compose_files
sync_compose_files() {
  local src="${GIT_REPO}/mykng/docker"
  local dst="${DEPLOY_BASE}"

  if [ ! -d "${src}" ]; then
    log_warn "git仓库中无 mykng/docker/ 目录，跳过同步"
    return 0
  fi

  mkdir -p "${dst}"
  cp -f "${src}"/docker-compose.*.yml "${dst}/" 2>/dev/null || true
  log_ok "compose 文件已同步到 ${dst}"
}

# ====== 确保基础设施网络存在 ======
# 用法: ensure_infra_network
ensure_infra_network() {
  if ! docker network ls --format '{{.Name}}' | grep -q '^kb-infra-net$'; then
    log_warn "kb-infra-net 网络不存在，请先启动基础设施层"
    log_info "执行: docker compose -p kb-infra -f ${DEPLOY_BASE}/docker-compose.infra.yml up -d"
    exit 1
  fi
  log_ok "kb-infra-net 网络就绪"
}

# ====== 停止旧服务 (仅指定服务，不影响其他) ======
# 用法: compose_stop_services <work_dir> <project> <compose_file> <service1> [service2...]
# 说明: 用 docker compose stop/rm 按服务名停止，避免容器名前缀不匹配问题
compose_stop_services() {
  local work_dir="$1"; shift
  local project="$1"; shift
  local compose_file="$1"; shift
  local services=("$@")

  cd "${work_dir}"

  # 先用 docker compose stop/rm 按服务名精确停止
  for svc in "${services[@]}"; do
    if docker compose -p "${project}" -f "${compose_file}" ps --services 2>/dev/null | grep -q "^${svc}$"; then
      docker compose -p "${project}" -f "${compose_file}" stop -t 10 "${svc}" 2>/dev/null || true
      docker compose -p "${project}" -f "${compose_file}" rm -f "${svc}" 2>/dev/null || true
      log_ok "已停止并移除旧容器: ${svc}"
      sleep 2
    fi
  done

  # 兜底: 清理可能残留的容器 (project-service 格式 和 裸服务名格式)
  for svc in "${services[@]}"; do
    # 尝试 project-service 格式 (如 kb-app-kb-file)
    local containers=$(docker ps -a --filter "name=${project}-${svc}" -q 2>/dev/null)
    if [ -n "${containers}" ]; then
      docker rm -f ${containers} 2>/dev/null || true
      log_ok "兜底清理残留容器: ${project}-${svc}"
      sleep 2
    fi
    # 尝试裸服务名 (如 kb-file)
    containers=$(docker ps -a --filter "name=^${svc}$" -q 2>/dev/null)
    if [ -n "${containers}" ]; then
      docker rm -f ${containers} 2>/dev/null || true
      log_ok "兜底清理残留容器: ${svc}"
      sleep 2
    fi
  done
}

# ====== 停止整个 compose project ======
# 用法: compose_down_all <work_dir> <project> <compose_file>
compose_down_all() {
  local work_dir="$1"
  local project="$2"
  local compose_file="$3"

  cd "${work_dir}"
  if docker compose -p "${project}" -f "${compose_file}" ps -q 2>/dev/null | grep -q .; then
    _heartbeat_start "docker compose down 停止容器" &
    _HB_PID=$!
    docker compose -p "${project}" -f "${compose_file}" down --remove-orphans --timeout 30 2>&1 || true
    _heartbeat_stop
    log_ok "compose down 完成"
    sleep 3
  else
    log_info "无正在运行的 compose project (首次部署或已清理)"
  fi

  # 兜底: 清理不属于任何 compose project 的裸名容器
  local orphan_names=$(docker ps -a --format '{{.Names}}' 2>/dev/null)
  for name in ${orphan_names}; do
    # 检查容器名是否以 project 开头 (如 kb-web-kb-ops-web 或裸名如 kb-web)
    if echo "${name}" | grep -qE "^${project}-|^[a-z].*-web$|^kb-"; then
      # 只清理属于当前 project 的容器
      if docker inspect "${name}" 2>/dev/null | grep -q "\"com.docker.compose.project\":\"${project}\"" 2>/dev/null; then
        docker rm -f "${name}" 2>/dev/null || true
        log_ok "兜底清理残留容器: ${name}"
        sleep 2
      fi
    fi
  done
}

# ====== 清理孤儿容器 ======
# 用法: cleanup_orphans <name1> [name2...]
cleanup_orphans() {
  local found=false
  for name in "$@"; do
    if docker ps -a --filter "name=^${name}$" -q 2>/dev/null | grep -q .; then
      found=true
      log_warn "发现残留容器: ${name}，强制删除"
      docker rm -f "${name}" 2>/dev/null || true
    fi
  done
  if [ "${found}" = false ]; then
    log_ok "无残留容器，环境干净"
  fi
}

# ====== 构建并启动指定服务 (不影响其他服务) ======
# 用法: compose_up_services <work_dir> <project> <compose_file> <service1> [service2...]
compose_up_services() {
  local work_dir="$1"; shift
  local project="$1"; shift
  local compose_file="$1"; shift
  local services=("$@")

  cd "${work_dir}"
  _heartbeat_start "docker compose up 启动服务" &
  _HB_PID=$!
  docker compose -p "${project}" -f "${compose_file}" \
    up -d --build --force-recreate "${services[@]}" 2>&1
  _heartbeat_stop

  echo ""
  log_ok "服务启动完成"
  docker compose -p "${project}" -f "${compose_file}" ps 2>/dev/null || \
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

# ====== 构建并启动全部服务 ======
# 用法: compose_up_all <work_dir> <project> <compose_file>
compose_up_all() {
  local work_dir="$1"
  local project="$2"
  local compose_file="$3"

  cd "${work_dir}"
  _heartbeat_start "docker compose up 启动服务" &
  _HB_PID=$!
  docker compose -p "${project}" -f "${compose_file}" \
    up -d --build --force-recreate --remove-orphans 2>&1
  _heartbeat_stop

  echo ""
  log_ok "服务启动完成"
  docker compose -p "${project}" -f "${compose_file}" ps 2>/dev/null || \
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

# ====== 健康检查 ======
# 用法: health_check <url> <container1> [container2...]
health_check() {
  local url="$1"; shift
  local containers=("$@")
  local max=${HEALTH_MAX_RETRIES}
  local interval=${HEALTH_INTERVAL}

  echo ""
  echo ">>> 健康检查 (最多等待 $(( max * interval / 60 )) 分钟) <<<"

  for i in $(seq 1 ${max}); do
    local now=$(date '+%H:%M:%S')
    local all_ok=true
    local errors=""

    # HTTP 检查
    if [ -n "${url}" ] && [ "${url}" != "none" ]; then
      local code=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 5 "${url}" 2>/dev/null || echo "000")
      if [ "${code}" = "200" ] || [ "${code}" = "201" ] || [ "${code}" = "302" ]; then
        : # OK
      else
        all_ok=false
        errors="${errors}HTTP(${code}) "
      fi
    fi

    # 容器状态检查
    for name in "${containers[@]}"; do
      if ! docker ps --filter "name=${name}" --filter "status=running" | grep -q .; then
        all_ok=false
        errors="${errors}${name} "
      fi
    done

    if ${all_ok}; then
      echo "  ✅ [${now}] 健康检查通过! ($i/${max})"
      return 0
    fi

    if [ $i -eq ${max} ]; then
      echo ""
      log_warn "健康检查超时: ${errors}"
      for name in "${containers[@]}"; do
        echo "     ${name}: $(docker ps -a --filter 'name='${name} --format '{{.Status}}' 2>/dev/null || echo '未找到')"
      done
      echo ""
      echo "  --- 最近日志 (${containers[0]}) ---"
      docker logs --tail=20 "${containers[0]}" 2>&1 || true
      return 1
    fi

    echo "  ⏳ [${now}] 等待中... ($i/${max}) [${errors}]"
    sleep ${interval}
  done
}

# ====== 清理悬空镜像 ======
prune_images() {
  local dangling=$(docker images -f "dangling=true" -q 2>/dev/null | wc -l)
  if [ "${dangling}" -gt 0 ]; then
    docker image prune -f 2>/dev/null || true
    log_ok "已清理 ${dangling} 个悬空镜像"
  else
    log_ok "无悬空镜像"
  fi
}

# ====== 等待端口释放 ======
# 用法: wait_port_release <port>
wait_port_release() {
  local port="$1"
  sleep 2
  if ss -tlnp | grep -q ":${port} "; then
    log_warn "端口 ${port} 仍被占用，等待释放..."
    sleep 5
    if ss -tlnp | grep -q ":${port} "; then
      fuser -k ${port}/tcp 2>/dev/null || true
      sleep 2
    fi
  fi
}
