/**
 * FRP 管理平台 SSO（OIDC authorization_code + PKCE）工具集。
 * 移植自 kb-ops/infra-monitor，适配 FRP 平台的 token 存储 key。
 *
 * 流程：
 *   startSsoLogin()    → 跳转 auth-center /oauth2/authorize
 *   handleSsoCallback()→ 回调页用 code + code_verifier 换 RS256 access_token / refresh_token
 *   refreshOidcToken() → 401 时用 refresh_token 静默续期
 */

const OIDC_ISSUER = 'https://auth.marschat.online'
const OIDC_CLIENT_ID = 'frp-manager'
const OIDC_REDIRECT_URI = `${window.location.origin}/sso-callback`

const STATE_KEY = 'frp_sso_state'
const VERIFIER_KEY = 'frp_sso_verifier'
const REDIRECT_KEY = 'frp_sso_redirect'

// ---------- 基础工具 ----------
function b64url(bytes) {
  let bin = ''
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i])
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function randomBytes(n) {
  const buf = new Uint8Array(n)
  if (crypto && crypto.getRandomValues) {
    crypto.getRandomValues(buf)
  } else {
    for (let i = 0; i < n; i++) buf[i] = Math.floor(Math.random() * 256)
  }
  return buf
}

async function createPkcePair() {
  const verifier = b64url(randomBytes(32))
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  const challenge = b64url(new Uint8Array(digest))
  return { verifier, challenge }
}

// ---------- SSO 流程 ----------

/** 发起 SSO 登录：记录回跳目标，302 到授权端点 */
export async function startSsoLogin(redirect = '/') {
  const state = b64url(randomBytes(16))
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

/** 回调页处理：校验 state → 换 token → 存储到 localStorage → 返回应跳转的站内目标 */
export async function handleSsoCallback(query) {
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

  const data = await res.json()

  sessionStorage.removeItem(STATE_KEY)
  sessionStorage.removeItem(VERIFIER_KEY)
  const target = sessionStorage.getItem(REDIRECT_KEY) || '/'
  sessionStorage.removeItem(REDIRECT_KEY)

  // 设置 token kind 为 oidc，并存入 token（使用 FRP 平台的 key）
  localStorage.setItem('frp_token_kind', 'oidc')
  localStorage.setItem('token', data.access_token)
  if (data.refresh_token) {
    localStorage.setItem('frp_refresh_token', data.refresh_token)
  }

  return target
}

/** OIDC 静默续期；成功刷新本地 token 对，失败抛出（由拦截器登出） */
export async function refreshOidcToken() {
  const refreshToken = localStorage.getItem('frp_refresh_token')
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

  const data = await res.json()
  localStorage.setItem('token', data.access_token)
  if (data.refresh_token) {
    localStorage.setItem('frp_refresh_token', data.refresh_token)
  }
}

/** 从 RS256 access_token 解出业务 claims */
export function decodeOidcClaims(token) {
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

// Token kind 管理（区分 legacy vs oidc）
export function getTokenKind() {
  return localStorage.getItem('frp_token_kind') || 'legacy'
}

export function setTokenKind(kind) {
  localStorage.setItem('frp_token_kind', kind)
}

export function isOidcToken() {
  return getTokenKind() === 'oidc'
}
