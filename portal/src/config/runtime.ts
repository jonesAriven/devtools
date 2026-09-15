/**
 * 运行时配置（T7 组件收敛，2026-09-15）
 *
 * 优先级：`public/app-config.json`（由 `scripts/gen-from-registry.py appconfig` 从
 * `apps-registry.yml` 派生）> 编译期 `import.meta.env` > 内置默认。
 *
 * 为什么要有它：此前 portal 的部署 base（`/portal`）、中心 issuer、clientId 散落在
 * `main.ts` / `router/index.ts` / `utils/sso.ts` / `utils/permissions.ts` / 两个视图里
 * **各写一份字面量** —— 改一个环境的入口要改 6 处并重新构建，与「接入即配置」相悖。
 * 现统一收敛到本文件；kb-ops / kb-web / infra-monitor 早前已是同构写法（本次对齐）。
 *
 * ⚠️ 与 kb-ops 试点同款实现：模块求值时用**同步 XHR** 保证配置在消费方常量初始化前就绪
 * （浏览器对主线程同步 XHR 有弃用告警，bootstrap 化为后续项）。
 */
let runtime: Record<string, string> = {}
try {
  const xhr = new XMLHttpRequest()
  xhr.open('GET', `${import.meta.env.BASE_URL}app-config.json`, false)
  xhr.send(null)
  if (xhr.status === 200) {
    runtime = JSON.parse(xhr.responseText)
  }
} catch {
  // 拉取失败回落编译期 env / 内置默认——本地开发无产物也能跑
}

/** 部署 base（子路径） */
export const CONTEXT_PATH = runtime.contextPath ?? import.meta.env.VITE_CONTEXT_PATH ?? '/portal'

/** 本应用 BFF API 前缀（portal-server 同源代理） */
export const API_BASE_URL = runtime.apiBase ?? import.meta.env.VITE_API_BASE_URL ?? '/portal/api'

/** auth-center 公网基址（机密客户端走 BFF，前端只用它做 OIDC 元数据/登出回跳） */
export const OIDC_ISSUER = runtime.issuer ?? import.meta.env.VITE_OIDC_ISSUER ?? 'https://auth.marschat.online'

/** 本应用的 OIDC client_id */
export const OIDC_CLIENT_ID = runtime.clientId ?? import.meta.env.VITE_OIDC_CLIENT_ID ?? 'marschat-portal'

/** 授权码回调地址（按 origin 动态生成，公网 / LAN / 本地开发三环境自适应） */
export const OIDC_REDIRECT_URI = `${window.location.origin}${CONTEXT_PATH}/auth/callback`

/** 开发态走 vite 代理，产物走 BFF 同源前缀 —— 供各视图/工具统一取用 */
export const BFF_API_BASE = import.meta.env.DEV ? '/api' : API_BASE_URL
