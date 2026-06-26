import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getProfile } from '@/api/auth'
import { storage } from '@/utils/storage'
import type { User } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(storage.get<string>('accessToken'))
  const refreshToken = ref<string | null>(storage.get<string>('refreshToken'))
  const user = ref<User | null>(storage.get<User>('user'))

  async function login(username: string, password: string) {
    const res = await loginApi({ username, password })
    accessToken.value = res.accessToken
    refreshToken.value = res.refreshToken
    storage.set('accessToken', res.accessToken)
    storage.set('refreshToken', res.refreshToken)
    await fetchProfile()
  }

  async function fetchProfile() {
    try {
      const profile = await getProfile()
      user.value = profile
      storage.set('user', profile)
    } catch {
      // ignore
    }
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      accessToken.value = null
      refreshToken.value = null
      user.value = null
      storage.clear()
    }
  }

  return { accessToken, refreshToken, user, login, fetchProfile, logout }
})
