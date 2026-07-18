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

  if [ -n "${pm_version}" ]; then
    echo "  📦 检测到 packageManager: pnpm@${pm_version}，锁定安装"
    npm install -g "pnpm@${pm_version}" 2>/dev/null
  else
    echo "  📦 未指定 packageManager，安装 pnpm@latest"
    npm install -g pnpm 2>/dev/null
  fi

  pnpm --version
  pnpm config set registry "${NEXUS_NPM_REGISTRY}"
  pnpm config set fetch-retries 5
  pnpm config set fetch-retry-factor 2
  pnpm config set fetch-retry-mintimeout 20000
  pnpm config set fetch-retry-maxtimeout 120000
  pnpm config set network-concurrency 1
}
