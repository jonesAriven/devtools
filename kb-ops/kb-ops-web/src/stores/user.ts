import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User, LoginRequest } from '@/types'
import { login as loginApi, logout as logoutApi, getUserProfile } from '@/api/auth'
import { setToken, setRefreshToken, getToken, sso } from '@/config'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const accessToken = ref<string | null>(getToken())
  const refreshToken = ref<string | null>(null)
  const profile = ref<User | null>(null)
  const isLoggedIn = ref(!!getToken())
  const username = ref('')

  async function login(usernameVal: string, password: string) {
    const data = await loginApi({ username: usernameVal, password })
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    profile.value = data.user
    isLoggedIn.value = true
    username.value = data.user.username
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
  }

  async function fetchProfile() {
    try {
      const data = await getUserProfile()
      profile.value = data.user
      isLoggedIn.value = true
    } catch {
      profile.value = null
      isLoggedIn.value = false
    }
  }

  /** SSO 登录成功后，用 OIDC claims 中的信息构建前端会话 */
  function setOidcSession(usernameVal: string) {
    accessToken.value = getToken()
    username.value = usernameVal
    isLoggedIn.value = true
  }

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
    username.value = ''
    // 统一登出（SLO）：销毁 IdP 会话 + 清本地，然后由组件导航离开。
    // ⚠️ sso.logout 内部会 clearLocalAuth() 并跳转，故此处不要再加 router.push。
    sso.logout()
  }

  function setProfile(user: User) {
    profile.value = user
  }

  return {
    accessToken,
    refreshToken,
    profile,
    isLoggedIn,
    username,
    login,
    logout,
    fetchProfile,
    setOidcSession,
    setProfile,
  }
})
