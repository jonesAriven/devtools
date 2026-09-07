import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User, LoginRequest } from '@/types'
import { login as loginApi, logout as logoutApi, getUserProfile } from '@/api/auth'
import { setToken, setRefreshToken, clearTokens, getToken } from '@/utils/token'
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
    }
    accessToken.value = null
    refreshToken.value = null
    profile.value = null
    isLoggedIn.value = false
    username.value = ''
    clearTokens()
    router.push('/login')
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
