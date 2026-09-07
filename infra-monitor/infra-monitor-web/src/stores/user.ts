import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'
import { setToken, setTokenKind, clearTokens, getToken } from '@/utils/token'
import router from '@/router'

export interface LoginResponse {
  token: string
  username: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const username = ref<string>('')
  const isLoggedIn = ref(!!getToken())

  async function login(usernameVal: string, password: string) {
    const data = await request.post('/auth/login', { username: usernameVal, password }) as unknown as LoginResponse
    token.value = data.token
    username.value = data.username
    isLoggedIn.value = true
    setTokenKind('legacy')
    setToken(data.token)
  }

  /** SSO 登录成功后，用 OIDC claims 中的 username 构建前端会话（infra-monitor 无 /auth/me，免后端查询） */
  function setOidcSession(usernameVal: string) {
    token.value = getToken()
    username.value = usernameVal
    isLoggedIn.value = true
  }

  async function logout() {
    token.value = null
    username.value = ''
    isLoggedIn.value = false
    clearTokens()
    router.push('/login')
  }

  return {
    token,
    username,
    isLoggedIn,
    login,
    setOidcSession,
    logout,
  }
})
