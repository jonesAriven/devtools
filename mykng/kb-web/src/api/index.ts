import axios from 'axios'
import type { R } from '@/types'
import { getToken, getRefreshToken, setToken, setRefreshToken, clearTokens } from '@/utils/token'
import { ElMessage } from 'element-plus'
import router from '@/router'

const ctx = import.meta.env.VITE_CONTEXT_PATH || '/kb'

const WHITE_LIST_PATHS = ['/auth/login', '/auth/refresh', '/share/verify/', '/share/detail/']

function isWhiteList(url: string): boolean {
  return WHITE_LIST_PATHS.some(p => url.includes(p))
}

const request = axios.create({
  baseURL: `${ctx}/api`,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    // blob/流式响应跳过业务码检查
    if (response.config.responseType === 'blob') {
      return response
    }
    const data = response.data as R<any>
    if (data.traceId) {
      response.headers['x-trace-id'] = data.traceId
    }
    if (data.code !== 0 && data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  async (error) => {
    const originalRequest = error.config
    const url = originalRequest?.url || ''

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isWhiteList(url)) {
        return Promise.reject(error)
      }

      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        clearTokens()
        if (!isWhiteList(url)) {
          router.push('/login')
        }
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(request(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const res = await axios.post(`${ctx}/api/auth/refresh`, { refreshToken })
        const data = res.data as R<{ accessToken: string; refreshToken: string }>
        if (data.code === 0 || data.code === 200) {
          setToken(data.data.accessToken)
          setRefreshToken(data.data.refreshToken)
          originalRequest.headers.Authorization = `Bearer ${data.data.accessToken}`
          pendingRequests.forEach((cb) => cb(data.data.accessToken))
          pendingRequests = []
          return request(originalRequest)
        } else {
          clearTokens()
          router.push('/login')
          return Promise.reject(error)
        }
      } catch {
        clearTokens()
        router.push('/login')
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    const message = error.response?.data?.message || error.message || '网络错误'
    if (!isWhiteList(url)) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
