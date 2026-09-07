import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { getToken, getRefreshToken, setToken, setRefreshToken, clearTokens, isOidcToken } from '@/utils/token'
import { refreshOidcToken } from '@/utils/sso'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { API_BASE_URL } from '@/config'

const WHITE_LIST_PATHS = ['/auth/login']

function isWhiteList(url: string): boolean {
  return WHITE_LIST_PATHS.some(p => url.includes(p))
}

type ApiResponse<T = any> = T

interface TypedAxiosInstance extends Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete' | 'patch' | 'request'> {
  // Omit 映射类型会丢失原接口的调用签名，401 静默续期里 request(originalRequest) 需要，手动补回
  <T = any, D = any>(config: AxiosRequestConfig<D>): Promise<ApiResponse<T>>
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>>
}

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
}) as TypedAxiosInstance

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

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const result = response.data
    if (result && (result.code === 0 || result.code === 200)) {
      return result.data
    }
    const message = result?.message || '请求失败'
    if (!isWhiteList(response.config.url || '')) {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  },
  async (error) => {
    const originalRequest = error.config
    const url = originalRequest?.url || ''

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isWhiteList(url)) {
        return Promise.reject(error)
      }

      const refreshToken = getRefreshToken()
      // 双 token 体系分流：OIDC（kb-auth RS256）走 SAS 静默续期；legacy 仅本地登录态，无 refresh 端点，直接登出
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
        if (isOidcToken()) {
          await refreshOidcToken()
          const newToken = getToken()
          if (!newToken) throw new Error('OIDC 续期后无 token')
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          pendingRequests.forEach((cb) => cb(newToken))
          pendingRequests = []
          return request(originalRequest)
        }
        // legacy：无 refresh 端点，直接登出
        clearTokens()
        router.push('/login')
        return Promise.reject(error)
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
