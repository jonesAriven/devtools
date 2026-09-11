#!/bin/bash
# ============================================================
# lib-build.sh — CI 构建侧公共函数库
# 运行环境: CI 容器 (maven/node 镜像)
# 被 build-*.sh 通过 source 引入
# ============================================================

# ====== 构建产物收集 ======
# 用法: collect_artifacts <产物名> <文件1> [文件2...]
# 说明: 将构建产物复制到 publish/<产物名>/ 目录
collect_artifacts() {
  local name="$1"; shift
  local files=("$@")
  local out_dir="publish/${name}"

  mkdir -p "${out_dir}"
  for f in "${files[@]}"; do
    if [ -f "${f}" ]; then
      cp "${f}" "${out_dir}/"
      echo "  OK  $(basename "${f}")"
    else
      echo "  WARN  ${f} not found"
    fi
  done
  echo "  Collected to ${out_dir}/"
}

# ====== 打包并推送产物 ======
# 用法: publish_artifact <产物名>
# 说明: 将 publish/<产物名>/ 打成 tar.gz，推送到共享目录
publish_artifact() {
  local name="$1"
  local tar_file="${name}-latest.tar.gz"

  tar czf "publish/${tar_file}" -C "publish/${name}" .
  cp "publish/${tar_file}" "${SHARED_DIR}/"

  # 验证产物已写入
  if [ ! -f "${SHARED_DIR}/${tar_file}" ]; then
    echo "ERROR: artifact not written to ${SHARED_DIR}/${tar_file}"
    exit 1
  fi

  local size=$(ls -lh "${SHARED_DIR}/${tar_file}" | awk '{print $5}')
  echo "  Published: ${tar_file} (${size})"
}

# ====== pnpm 配置 (前端构建用) ======
# 用法: setup_pnpm <project_dir>
# 说明: 优先读 <project_dir>/package.json 里的 packageManager 字段锁定版本
#       (2026-07-18 增强：支持 packageManager 字段，避免 pnpm@latest 与老 lockfile 不兼容)
setup_pnpm() {
  local project_dir="${1:-.}"
  local pkg_file="${project_dir}/package.json"
  local pm_version=""

  if [ -f "${pkg_file}" ]; then
    pm_version=$(grep -oP '"packageManager"\s*:\s*"pnpm@\K[^"]+' "${pkg_file}" || true)
  fi

  # 🔴 必须显式 --registry 走 Nexus（2026-09-11 实测事故）
  #   直连 registry.npmjs.org 在本网络下单请求就要 12s+（Nexus 同请求 0.65s），
  #   `npm install -g pnpm` 会长时间挂死在「解析 latest dist-tag / 下载 tarball」上：
  #   实测 kb-ops-web(#578) / infra-monitor-web(#579) 两条流水线卡在该步骤超过 18 分钟，
  #   最终只能手动 cancel。未声明 packageManager 的应用（kb-ops-web / infra-monitor-web）
  #   走的是 `npm install -g pnpm`（无版本号）分支，必然要联网查 latest，因此必挂。
  #   另：pnpm@latest 已到 11.x，与本仓 lockfileVersion 9.0 存在兼容风险，故缺省锁 9.15.9。
  local nexus_reg="${NEXUS_NPM_REGISTRY:-http://192.168.31.105:8081/repository/npm-public/}"
  if [ -z "${pm_version}" ]; then
    pm_version="${PNPM_DEFAULT_VERSION:-9.15.9}"
    echo "  📦 未指定 packageManager，使用默认 pnpm@${pm_version}（不再装 latest）"
  else
    echo "  📦 检测到 packageManager: pnpm@${pm_version}，锁定安装"
  fi

  echo "  📦 经 Nexus 安装 pnpm@${pm_version}: ${nexus_reg}"
  npm install -g --registry "${nexus_reg}" --no-fund --no-audit "pnpm@${pm_version}" \
    || echo "  WARN npm 全局安装 pnpm@${pm_version} 失败，尝试复用镜像内已有的 pnpm"

  pnpm --version
  pnpm config set registry "${NEXUS_NPM_REGISTRY}"
  pnpm config set fetch-retries 5
  pnpm config set fetch-retry-factor 2
  pnpm config set fetch-retry-mintimeout 20000
  pnpm config set fetch-retry-maxtimeout 120000
  pnpm config set network-concurrency 1
}
