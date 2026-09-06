import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, type LoginRequest } from '@/api/auth'

const TOKEN_KEY = 'portal_token'
const USER_KEY = 'portal_user'
const ROLE_KEY = 'portal_role'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const username = ref<string>(localStorage.getItem(USER_KEY) || '')
  const role = ref<string>(localStorage.getItem(ROLE_KEY) || 'user')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'admin')

  function setSession(res: { token?: string; accessToken?: string; username?: string; role?: string }, fallbackUsername?: string) {
    const tokenVal = res.token || res.accessToken || ''
    token.value = tokenVal
    username.value = res.username || fallbackUsername || ''
    role.value = res.role || 'user'
    localStorage.setItem(TOKEN_KEY, tokenVal)
    localStorage.setItem(USER_KEY, username.value)
    localStorage.setItem(ROLE_KEY, role.value)
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

  function logout() {
    token.value = ''
    username.value = ''
    role.value = 'user'
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(ROLE_KEY)
  }

  return {
    token,
    username,
    role,
    isLoggedIn,
    isAdmin,
    login,
    ssoExchange,
    logout
  }
})
