import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

export function useAuth() {
  const router = useRouter()
  const userStore = useUserStore()
  const loading = ref(false)

  async function login(username: string, password: string) {
    loading.value = true
    try {
      await userStore.login(username, password)
      ElMessage.success('登录成功')
      const redirect = (router.currentRoute.value.query.redirect as string) || '/kb/dashboard'
      router.push(redirect)
    } catch (error: any) {
      ElMessage.error(error.message || '登录失败')
      throw error
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    await userStore.logout()
    ElMessage.success('已退出登录')
  }

  return {
    loading,
    login,
    logout,
  }
}
