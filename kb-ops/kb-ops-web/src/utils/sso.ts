/**
 * kb-ops SSO（OIDC authorization_code + PKCE）工具集。
 * 移植自 infra-monitor / kb-web，按 kb-ops 的 base=/ops 调整 redirect_uri。
 *
 * 流程：
 *   startSsoLogin()    → 跳转 auth-center /oauth2/authorize
 *   handleSsoCallback()→ 回调页用 code + code_verifier 换 RS256 access_token / refresh_token
 *   refreshOidcToken() → 401 时用 refresh_token 静默续期
 */

// OIDC 配置（与 auth-center 对齐）
const OIDC_ISSUER = 'https://auth.marschat.online'
const OIDC_CLIENT_ID = 'kb-ops'
const OIDC_REDIRECT_URI = `${window.location.origin}/ops/sso-callback`

const STATE_KEY = 'kb_ops_sso_state'
const VERIFIER_KEY = 'kb_ops_sso_verifier'
const REDIRECT_KEY = 'kb_ops_sso_redirect'

interface OidcTokenResponse {
  access_token: string
  refresh_token?: string
  token_type: string
  expires_in: number
  scope?: string
}

/** 生成 43-128 位随机 code_verifier 并派生 S256 challenge */
async function createPkcePair(): Promise<{ verifier: string; challenge: string }> {
  const bytes = new Uint8Array(32)
  crypto.getRandomValues(bytes)
  const verifier = base64UrlEncode(bytes)
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  const challenge = base64UrlEncode(new Uint8Array(digest))
  return { verifier, challenge }
}

function base64UrlEncode(bytes: Uint8Array): string {
  let bin = ''
  bytes.forEach(b => (bin += String.fromCharCode(b)))
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** 发起 SSO 登录：记录回跳目标，302 到授权端点 */
export async function startSsoLogin(redirect = '/dashboard'): Promise<void> {
  const state = base64UrlEncode(crypto.getRandomValues(new Uint8Array(16)))
  const { verifier, challenge } = await createPkcePair()
  sessionStorage.setItem(STATE_KEY, state)
  sessionStorage.setItem(VERIFIER_KEY, verifier)
  sessionStorage.setItem(REDIRECT_KEY, redirect)

  const params = new URLSearchParams({
    client_id: OIDC_CLIENT_ID,
    redirect_uri: OIDC_REDIRECT_URI,
    response_type: 'code',
    scope: 'openid profile',
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  })
  window.location.assign(`${OIDC_ISSUER}/oauth2/authorize?${params.toString()}`)
}

/** 回调页处理：校验 state → 换 token → 落库 → 返回应跳转的站内目标 */
export async function handleSsoCallback(query: URLSearchParams): Promise<string> {
  const code = query.get('code')
  const state = query.get('state')
  const error = query.get('error')

  if (error) {
    throw new Error(`统一认证拒绝: ${query.get('error_description') || error}`)
  }
  if (!code) {
    throw new Error('授权回调缺少 code')
  }
  const savedState = sessionStorage.getItem(STATE_KEY)
  const verifier = sessionStorage.getItem(VERIFIER_KEY)
  if (!savedState || savedState !== state) {
    throw new Error('state 校验失败，请重新发起登录')
  }
  if (!verifier) {
    throw new Error('PKCE 凭据丢失，请重新发起登录')
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: OIDC_REDIRECT_URI,
    client_id: OIDC_CLIENT_ID,
    code_verifier: verifier,
  })
  const res = await fetch(`${OIDC_ISSUER}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!res.ok) {
    const detail = await res.text().catch(() => '')
    throw new Error(`换取令牌失败(HTTP ${res.status}): ${detail.slice(0, 120)}`)
  }
  const data = (await res.json()) as OidcTokenResponse

  sessionStorage.removeItem(STATE_KEY)
  sessionStorage.removeItem(VERIFIER_KEY)
  const target = sessionStorage.getItem(REDIRECT_KEY) || '/dashboard'
  sessionStorage.removeItem(REDIRECT_KEY)

  // 设置 token kind 为 oidc，并存入 token
  setTokenKind('oidc')
  setToken(data.access_token)
  if (data.refresh_token) {
    setRefreshToken(data.refresh_token)
  }
  return target
}

/** OIDC 静默续期；成功刷新本地 token 对，失败抛出（由拦截器登出） */
export async function refreshOidcToken(): Promise<void> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('无 refresh_token')
  }
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    refresh_token: refreshToken,
    client_id: OIDC_CLIENT_ID,
  })
  const res = await fetch(`${OIDC_ISSUER}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!res.ok) {
    throw new Error(`OIDC 续期失败(HTTP ${res.status})`)
  }
  const data = (await res.json()) as OidcTokenResponse
  setToken(data.access_token)
  if (data.refresh_token) {
    setRefreshToken(data.refresh_token)
  }
}

/** 从 RS256 access_token 解出业务 claims（uid/username/realm/role），用于免 /me 构建会话 */
export function decodeOidcClaims(token: string): { uid?: string; username?: string; realm?: string; role?: string } {
  try {
    const payload = token.split('.')[1]
    const json = new TextDecoder().decode(
      Uint8Array.from(atob(payload.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0))
    )
    return JSON.parse(json)
  } catch {
    return {}
  }
}

// 引入 token 工具函数（避免循环依赖）
import { setToken, getRefreshToken, setRefreshToken } from './token'

// Token kind 管理（区分 legacy HS256 vs oidc RS256）
const TOKEN_KIND_KEY = 'kb_ops_token_kind'

export type TokenKind = 'legacy' | 'oidc'

export function getTokenKind(): TokenKind {
  return (localStorage.getItem(TOKEN_KIND_KEY) as TokenKind) || 'legacy'
}

export function setTokenKind(kind: TokenKind): void {
  localStorage.setItem(TOKEN_KIND_KEY, kind)
}

export function isOidcToken(): boolean {
  return getTokenKind() === 'oidc'
}
