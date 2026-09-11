import axios, { type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const authBaseURL = import.meta.env.DEV ? '/api/auth' : '/portal/api/auth'
const portalBaseURL = import.meta.env.DEV ? '/api/portal' : '/portal/api/sys'
const adminBaseURL = import.meta.env.DEV ? '/api/admin' : '/portal/api/admin'

export const authRequest = axios.create({
  baseURL: authBaseURL,
  timeout: 30000
})

export const adminRequest = axios.create({
  baseURL: adminBaseURL,
  timeout: 30000
})

export const portalRequest = axios.create({
  baseURL: portalBaseURL,
  timeout: 30000
})

function addTokenInterceptor(instance: AxiosInstance) {
  instance.interceptors.request.use(
    (config) => {
      const userStore = useUserStore()
      if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`
      }
      return config
    },
    (error) => Promise.reject(error)
  )
}

function addResponseInterceptor(instance: AxiosInstance) {
  instance.interceptors.response.use(
    (response) => {
      const result = response.data
      if (result && result.code === 200) {
        return result.data
      }
      return Promise.reject(new Error(result?.message || '请求失败'))
    },
    (error) => {
      const userStore = useUserStore()
      if (error.response?.status === 401) {
        // ⚠️ 这里只能用 clearSession（清本地），**不能** userStore.logout() ——
        //    logout 是统一登出（SLO），会把全局 IdP 会话一起销毁。
        //    会话过期场景下我们希望：清本地 → 回登录页 → 登录页探到 IdP 会话 →
        //    静默免登无感把用户送回来（这就是 portal 这条 BFF 链路的"自动续期"）。
        userStore.clearSession()
        ElMessage.error('登录已过期，正在重新认证…')
        // reauth=1 供登录页识别这是一次"过期重认证"，避免异常情况下无限往返
        window.location.href = '/portal/login?reauth=1'
      } else {
        ElMessage.error(error.response?.data?.message || error.message || '请求失败')
      }
      return Promise.reject(error)
    }
  )
}

addTokenInterceptor(authRequest)
addTokenInterceptor(portalRequest)
addTokenInterceptor(adminRequest)
addResponseInterceptor(authRequest)
addResponseInterceptor(portalRequest)
addResponseInterceptor(adminRequest)

export default portalRequest
