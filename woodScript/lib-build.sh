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
setup_pnpm() {
  npm install -g pnpm 2>/dev/null
  pnpm config set registry "${NEXUS_NPM_REGISTRY}"
  pnpm config set fetch-retries 5
  pnpm config set fetch-retry-factor 2
  pnpm config set fetch-retry-mintimeout 20000
  pnpm config set fetch-retry-maxtimeout 120000
  pnpm config set network-concurrency 1
}
