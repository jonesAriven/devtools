import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, type LoginRequest } from '@/api/auth'

const TOKEN_KEY = 'portal_token'
const USER_KEY = 'portal_user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const username = ref<string>(localStorage.getItem(USER_KEY) || '')

  const isLoggedIn = computed(() => !!token.value)

  async function login(credentials: LoginRequest) {
    const res = await loginApi(credentials)
    const tokenVal = res.accessToken || res.token || ''
    token.value = tokenVal
    username.value = res.username || credentials.username
    localStorage.setItem(TOKEN_KEY, tokenVal)
    localStorage.setItem(USER_KEY, res.username || credentials.username)
    return res
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    username,
    isLoggedIn,
    login,
    logout
  }
})
