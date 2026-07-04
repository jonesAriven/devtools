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

  async function login(username: string, password: string) {
    const res = await loginApi({ username, password })
    const data = res.data.data
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    profile.value = data.user
    isLoggedIn.value = true
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

  async function logout() {
    try {
      await logoutApi()
    } catch {
    }
    accessToken.value = null
    refreshToken.value = null
    profile.value = null
    isLoggedIn.value = false
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
    login,
    logout,
    fetchProfile,
    setProfile,
  }
})
