import axios, { type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const authBaseURL = import.meta.env.DEV ? '/api/auth' : '/portal/api/auth'
const portalBaseURL = import.meta.env.DEV ? '/api/portal' : '/portal/api/sys'

export const authRequest = axios.create({
  baseURL: authBaseURL,
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
        userStore.logout()
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/portal/login'
      } else {
        ElMessage.error(error.response?.data?.message || error.message || '请求失败')
      }
      return Promise.reject(error)
    }
  )
}

addTokenInterceptor(authRequest)
addTokenInterceptor(portalRequest)
addResponseInterceptor(authRequest)
addResponseInterceptor(portalRequest)

export default portalRequest
