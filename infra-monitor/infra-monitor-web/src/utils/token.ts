const ACCESS_TOKEN_KEY = 'infra_access_token'
const REFRESH_TOKEN_KEY = 'infra_refresh_token'
const TOKEN_KIND_KEY = 'infra_token_kind'

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

export function getToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function removeRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function clearTokens(): void {
  removeToken()
  removeRefreshToken()
  localStorage.removeItem(TOKEN_KIND_KEY)
}
