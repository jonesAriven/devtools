import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'
import { setToken, clearTokens, getToken } from '@/utils/token'
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
    setToken(data.token)
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
    logout,
  }
})
