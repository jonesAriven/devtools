/**
 * 运行时配置（Phase 3 · app-config.json，同 kb-ops 试点模式）
 *
 * 优先级：运行时 public/app-config.json（apps-registry.yml 派生）> 编译期 env > 内置默认。
 * ⚠️ 试点实现用同步 XHR（模块求值时保证填充）；bootstrap 化为后续项。
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
  // 拉取失败回落编译期 env/默认值
}

export const CONTEXT_PATH = runtime.contextPath ?? import.meta.env.VITE_CONTEXT_PATH ?? '/infra'
export const API_BASE_URL = runtime.apiBase ?? import.meta.env.VITE_API_BASE_URL ?? '/infra/infra-api'
export const AUTH_BASE_URL = runtime.authApiBase ?? import.meta.env.VITE_AUTH_BASE_URL ?? '/infra/infra-api'

/**
 * auth-center 统一认证（OIDC public client + PKCE，2026-09-07）
 * issuer 公网与内网统一走公网域名（与 auth-center AUTH_ISSUER 一致）；
 * redirect_uri 按 origin 动态生成，公网/LAN/本地开发三环境自适应，并拼接 /infra base 前缀。
 */
export const OIDC_ISSUER = runtime.issuer ?? import.meta.env.VITE_OIDC_ISSUER ?? 'https://auth.marschat.online'
export const OIDC_CLIENT_ID = runtime.clientId ?? import.meta.env.VITE_OIDC_CLIENT_ID ?? 'marschat-inframon'
export const OIDC_REDIRECT_URI = `${window.location.origin}${CONTEXT_PATH}/sso-callback`
