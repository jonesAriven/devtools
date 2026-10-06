import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User } from '@/types'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { getUserProfile } from '@/api/user'
import { setToken, setRefreshToken, getToken, setTokenKind, ssoLogout } from '@/config'

export const useUserStore = defineStore('user', () => {
  const accessToken = ref<string | null>(getToken())
  const refreshToken = ref<string | null>(null)
  const profile = ref<User | null>(null)
  const isLoggedIn = ref(!!getToken())

  async function login(username: string, password: string) {
    const res = await loginApi({ username, password })
    const data = res.data.data
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    profile.value = data.user
    isLoggedIn.value = true
    setTokenKind('legacy')
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
  }

  async function fetchProfile() {
    try {
      const res = await getUserProfile()
      profile.value = res.data.data
      isLoggedIn.value = true
    } catch {
      profile.value = null
      isLoggedIn.value = false
    }
  }

  /**
   * 退出登录 —— **统一登出（SLO）**（Phase 6）
   *
   * 与旧实现的区别：旧版只 `clearTokens()` + 跳 /login，**IdP 会话仍然活着**，
   * 于是再打开本应用或任一兄弟应用，静默免登会把你**直接免密登回去** —— 表现为"根本退不出去"。
   *
   * 现在改为走 auth-center `/auth/slo`：
   * 清SSO Cookie → 带 id_token_hint 跳 `/connect/logout` 销毁 IdP 会话 → 回跳登录页。
   * ⚠️ `ssoLogout` 会导航离开，所以后面不需要再 router.push。
   */
  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 忽略登出接口错误（认证中心不可达也必须能退出去）
    }
    accessToken.value = null
    refreshToken.value = null
    profile.value = null
    isLoggedIn.value = false
    // ⚠️ 必须用 `ssoLogout`（= stopSessionWatcher + sso.logout），**不能直接调 `sso.logout()`**：
    //    登出会导航离开，若此时会话监视器仍在跑，它可能在跳转途中再判定一次「会话已失」
    //    → 造成多余的二次跳转。旧 `utils/sso.ts` 的 `ssoLogout` 正是这个语义，迁移后不能丢。
    //    内部已 clearLocalAuth()，无需再 clearTokens()。
    ssoLogout()
  }

  function setProfile(user: User) {
    profile.value = user
  }

  return {
    accessToken,
    refreshToken,
    profile,
    isLoggedIn,
    login,
    logout,
    fetchProfile,
    setProfile,
  }
})
