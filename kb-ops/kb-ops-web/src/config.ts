/**
 * 运行时配置（Phase 3 · R6 app-config.json 试点）
 *
 * 优先级：运行时 public/app-config.json（apps-registry.yml 派生，同产物多环境）
 *       > 编译期 import.meta.env > 内置默认。
 *
 * ⚠️ 试点实现用同步 XHR（模块求值时保证填充，消费方同步常量引用零改动）；
 *    浏览器对主线程同步 XHR 有弃用告警，main.ts bootstrap 化为后续项。
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
  // 拉取失败回落编译期 env/默认值——本地开发无产物也能跑
}

export const CONTEXT_PATH = runtime.contextPath ?? import.meta.env.VITE_CONTEXT_PATH ?? '/ops'
export const API_BASE_URL = runtime.apiBase ?? import.meta.env.VITE_API_BASE_URL ?? '/ops/ops-api'
export const AUTH_BASE_URL = runtime.authApiBase ?? import.meta.env.VITE_AUTH_BASE_URL ?? '/ops-api'

/**
 * auth-center 统一认证（OIDC public client + PKCE）。
 *
 * 🔴 2026-09-15（T7 组件收敛）：此前 `sso.ts` 把 issuer / clientId **硬编码**在代码里，
 * 而同级 `public/app-config.json`（由 apps-registry.yml 派生）本来就带着这两个字段 ——
 * 结果「配置化接入」对 kb-ops 只生效了一半：改 registry 不生效，必须改代码重新构建。
 * 现统一从这里读，与 infra-monitor-web（标准参考实现）同构。
 *
 * `OIDC_REDIRECT_URI` 按 origin 动态生成（公网 / LAN / 本地开发三环境自适应），
 * 并拼 `CONTEXT_PATH` 前缀（＝`/ops/sso-callback`），绝不硬编码域名。
 */
export const OIDC_ISSUER = runtime.issuer ?? import.meta.env.VITE_OIDC_ISSUER ?? 'https://auth.marschat.online'
export const OIDC_CLIENT_ID = runtime.clientId ?? import.meta.env.VITE_OIDC_CLIENT_ID ?? 'marschat-kbops'
export const OIDC_REDIRECT_URI = `${window.location.origin}${CONTEXT_PATH}/sso-callback`
