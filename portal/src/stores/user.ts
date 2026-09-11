import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, ssoExchangeApi, type LoginRequest } from '@/api/auth'
import { logout as ssoLogoutPortal } from '@/utils/sso'

const TOKEN_KEY = 'portal_token'
const USER_KEY = 'portal_user'
const ROLE_KEY = 'portal_role'
// auth-center 用户 id：会话监视器「身份一致性守卫」的本地身份依据（0.5.4+）
const AUTH_UID_KEY = 'portal_auth_uid'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const username = ref<string>(localStorage.getItem(USER_KEY) || '')
  const role = ref<string>(localStorage.getItem(ROLE_KEY) || 'user')
  const authUid = ref<string>(localStorage.getItem(AUTH_UID_KEY) || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'admin')

  function setSession(res: { token?: string; accessToken?: string; username?: string; role?: string; authUid?: string }, fallbackUsername?: string) {
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
  }

  async function login(credentials: LoginRequest) {
    const res = await loginApi(credentials)
    setSession(res as any, credentials.username)
    return res
  }

  async function ssoExchange(code: string, state: string) {
    const res = await ssoExchangeApi(code, state)
    setSession(res as any)
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
