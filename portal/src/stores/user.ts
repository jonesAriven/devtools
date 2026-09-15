import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { initTokenConfig, setTokenKind } from '@marschat/auth-components'
import { login as loginApi, ssoExchangeApi, type LoginRequest } from '@/api/auth'
import { logout as ssoLogoutPortal } from '@/utils/sso'

const TOKEN_KEY = 'portal_token'
const USER_KEY = 'portal_user'
const ROLE_KEY = 'portal_role'
// auth-center 用户 id：会话监视器「身份一致性守卫」的本地身份依据（0.5.4+）
const AUTH_UID_KEY = 'portal_auth_uid'

/**
 * 登录渠道标记（组件库 `token_kind` 约定，同族：infra_token_kind）。
 *
 * ★ 为什么必须区分渠道：会话监视器探的是 auth-center `/auth/session`（**IdP 会话**），
 *   而 portal 的账密 / 邮箱验证码走的是 **portal-server BFF 换票**——auth-center 的
 *   Set-Cookie 落在后端进程里，**浏览器侧根本没有 IdP 会话**。若对这类会话也启动监视器，
 *   探针恒返回 `authenticated:false` → 组件默认 `confirmCount:1` + `redirectOnLost:true`
 *   且 `start()` 会在 3 秒后首探 → 每次整页刷新都被判「他处已登出」→ 跳 `?slo=1`。
 *   只有浏览器侧真走过 OIDC 授权跳转的会话才值得监视。
 */
const TOKEN_KIND_KEY = 'portal_token_kind'
initTokenConfig({ tokenKindKey: TOKEN_KIND_KEY })

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const username = ref<string>(localStorage.getItem(USER_KEY) || '')
  const role = ref<string>(localStorage.getItem(ROLE_KEY) || 'user')
  const authUid = ref<string>(localStorage.getItem(AUTH_UID_KEY) || '')

  const isLoggedIn = computed(() => !!token.value)
  // ⚠️ 必须同时认 superadmin：auth-center 的超管账号（user.role='superadmin'，§22.1）
  //    是平台唯一的最高权限账号；只比 'admin' 会把超管挡在 portal 用户管理之外
  //    （2026-09-13 浏览器实测：portal_role=superadmin → /users 被重定向回工作台，
  //      BFF /portal/api/admin/users 返回 403「需要管理员权限」）。
  const isAdmin = computed(() => role.value === 'admin' || role.value === 'superadmin')

  /**
   * 写入会话。
   *
   * @param kind 登录渠道 —— `'oidc'` 仅由 SSO 回调（`ssoExchange`）传入；
   *             账密 / 邮箱验证码默认 `'legacy'`（浏览器侧无 IdP 会话）。
   *             该标记决定 `main.ts` 是否启动会话监视器（SLO 联动）。
   */
  function setSession(
    res: { token?: string; accessToken?: string; username?: string; role?: string; authUid?: string },
    fallbackUsername?: string,
    kind: 'oidc' | 'legacy' = 'legacy'
  ) {
    const tokenVal = res.token || res.accessToken || ''
    token.value = tokenVal
    username.value = res.username || fallbackUsername || ''
    role.value = res.role || 'user'
    authUid.value = res.authUid || ''
    localStorage.setItem(TOKEN_KEY, tokenVal)
    localStorage.setItem(USER_KEY, username.value)
    localStorage.setItem(ROLE_KEY, role.value)
    if (authUid.value) localStorage.setItem(AUTH_UID_KEY, authUid.value)
    else localStorage.removeItem(AUTH_UID_KEY)
    setTokenKind(kind)
  }

  async function login(credentials: LoginRequest) {
    const res = await loginApi(credentials)
    // 账密走 portal-server BFF 换票，浏览器侧没有 IdP 会话 → legacy
    setSession(res as any, credentials.username, 'legacy')
    return res
  }

  async function ssoExchange(code: string, state: string) {
    const res = await ssoExchangeApi(code, state)
    // SSO 是浏览器侧 OIDC 授权跳转，确有 IdP 会话 → oidc（可被会话监视器监视）
    setSession(res as any, undefined, 'oidc')
    return res
  }

  /**
   * 仅清本地会话（**不碰 IdP 会话**）。
   *
   * 供 401 拦截器使用：会话过期时要"清干净再走登录页"，而不能顺手把
   * 全局 IdP 会话也干掉 —— 否则登录页的静默免登就没机会把你免密送回来。
   */
  function clearSession() {
    token.value = ''
    username.value = ''
    role.value = 'user'
    authUid.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(ROLE_KEY)
    localStorage.removeItem(AUTH_UID_KEY)
    // 渠道标记一并清掉：否则登出后残留 'oidc'，下次账密会话可能被误判为可监视
    localStorage.removeItem(TOKEN_KIND_KEY)
  }

  /**
   * 退出登录 —— **统一登出（SLO）**（Phase 6）
   *
   * 旧实现只清 3 个 localStorage key，**IdP 会话仍然活着**，于是再打开
   * portal 或任一兄弟应用都会被静默免登**直接免密登回去** —— "退不掉"。
   * 现在交给 auth-center `/auth/slo`：清 SSO Cookie → 销毁 IdP 会话 → 回跳登录页。
   * ⚠️ 会导航离开，调用方不要再 router.push。
   */
  function logout() {
    clearSession()
    ssoLogoutPortal()
  }

  return {
    token,
    username,
    role,
    authUid,
    isLoggedIn,
    isAdmin,
    login,
    ssoExchange,
    clearSession,
    logout
  }
})
