/**
 * 统一配置（Phase 3 · R6 运行时配置）
 *
 * 优先级：运行时 `public/app-config.json`（apps-registry.yml 派生，同产物多环境）
 *       > 编译期 import.meta.env > 内置默认。
 *
 * ⚠️ 与 kb-ops 试点同口径：模块求值时用同步 XHR 填充，消费方同步常量引用零改动；
 *    浏览器对主线程同步 XHR 有弃用告警，后续可 bootstrap 化。
 */

/** 运行时配置（public/app-config.json，缺失/解析失败则回落编译期 env/默认值） */
let runtime: Record<string, string> = {}
try {
  const xhr = new XMLHttpRequest()
  xhr.open('GET', `${import.meta.env.BASE_URL}app-config.json`, false)
  xhr.send(null)
  if (xhr.status === 200) {
    // ⚠️ 生成器（gen-from-registry.py）产物首行是 `// AUTO-GENERATED ...` 横幅注释，
    //    不是合法 JSON —— 直接 JSON.parse 会抛错，导致运行时配置静默失效（回落 env）。
    //    这里先剥掉 `//` 注释行再解析，保证运行时覆盖真正生效。
    const raw = xhr.responseText.replace(/^\s*\/\/.*$/gm, '')
    runtime = JSON.parse(raw)
  }
} catch {
  // 拉取/解析失败回落编译期 env/默认值——本地开发无产物也能跑
}

/** 统一上下文路径（唯一派生点） */
export const CONTEXT_PATH = runtime.contextPath ?? import.meta.env.VITE_CONTEXT_PATH ?? '/kb'

/** 后端 API 基址 */
export const API_BASE_URL = runtime.apiBase ?? import.meta.env.VITE_API_BASE_URL ?? '/kb/api'

/**
 * auth-center 统一认证（OIDC public client + PKCE，2026-09-07）
 * issuer 公网与内网统一走公网域名（与 auth-center AUTH_ISSUER 一致）；
 * redirect_uri 按 origin 动态生成，公网/LAN/本地开发三环境自适应。
 */
export const OIDC_ISSUER = runtime.issuer ?? import.meta.env.VITE_OIDC_ISSUER ?? 'https://auth.marschat.online'
export const OIDC_CLIENT_ID = runtime.clientId ?? import.meta.env.VITE_OIDC_CLIENT_ID ?? 'marschat-kbweb'
export const OIDC_REDIRECT_URI = `${window.location.origin}${CONTEXT_PATH}/sso-callback`
