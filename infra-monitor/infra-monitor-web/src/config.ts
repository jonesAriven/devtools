export const CONTEXT_PATH = import.meta.env.VITE_CONTEXT_PATH || '/infra'
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/infra/infra-api'
export const AUTH_BASE_URL = import.meta.env.VITE_AUTH_BASE_URL || '/infra/infra-api'

/**
 * auth-center 统一认证（OIDC public client + PKCE，2026-09-07）
 * issuer 公网与内网统一走公网域名（与 auth-center AUTH_ISSUER 一致）；
 * redirect_uri 按 origin 动态生成，公网/LAN/本地开发三环境自适应，并拼接 /infra base 前缀。
 */
export const OIDC_ISSUER = import.meta.env.VITE_OIDC_ISSUER || 'https://auth.marschat.online'
export const OIDC_CLIENT_ID = import.meta.env.VITE_OIDC_CLIENT_ID || 'marschat-inframon'
export const OIDC_REDIRECT_URI = `${window.location.origin}${CONTEXT_PATH}/sso-callback`
