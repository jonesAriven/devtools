import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { isOidcToken, refreshOidcToken } from './sso'

const request = axios.create({
  baseURL: '/frp_manager/api',
  timeout: 15000
})

// Request interceptor - add token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

let isRefreshing = false
let pendingRequests = []

// Response interceptor - handle errors + OIDC token refresh
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  async error => {
    const originalRequest = error.config
    
    // 401 处理：尝试 OIDC token 刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      // 如果是 OIDC token，尝试静默续期
      if (isOidcToken() && localStorage.getItem('frp_refresh_token')) {
        if (isRefreshing) {
          // 正在刷新，排队等待
          return new Promise((resolve) => {
            pendingRequests.push((token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(request(originalRequest))
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          await refreshOidcToken()
          const newToken = localStorage.getItem('token')
          // 重放排队的请求
          pendingRequests.forEach(cb => cb(newToken))
          pendingRequests = []
          // 重放当前请求
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return request(originalRequest)
        } catch (refreshError) {
          // 刷新失败，清除 token 并跳转登录
          pendingRequests = []
          localStorage.removeItem('token')
          localStorage.removeItem('frp_refresh_token')
          localStorage.removeItem('frp_token_kind')
          router.push('/login')
          ElMessage.error('登录已过期，请重新登录')
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }

      // legacy token 或无 refresh_token，直接登出
      localStorage.removeItem('token')
      localStorage.removeItem('frp_refresh_token')
      localStorage.removeItem('frp_token_kind')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
      return Promise.reject(error)
    }

    if (error.response?.status === 403) {
      ElMessage.error('没有权限访问')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
