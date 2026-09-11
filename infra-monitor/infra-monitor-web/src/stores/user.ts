import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'
import { setToken, setTokenKind, getToken } from '@/utils/token'
import { ssoLogout } from '@/utils/sso'
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
    // 统一登出（SLO）：销毁 IdP 会话 + 清本地，然后由组件导航离开。
    // ⚠️ ssoLogout 内部会 clearLocalAuth() 并跳转，故此处不要再加 router.push。
    // ⚠️ 签名是「已绑定配置」的 `ssoLogout(options?)`，不要再传 SSO_CONFIG（会当成 options）
    ssoLogout()
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
