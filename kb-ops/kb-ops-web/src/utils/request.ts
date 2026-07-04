import axios from 'axios'
import type { R } from '@/types'
import { getToken, getRefreshToken, setToken, setRefreshToken, clearTokens } from '@/utils/token'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { API_BASE_URL, AUTH_BASE_URL } from '@/config'

const WHITE_LIST_PATHS = ['/login', '/refresh']

function isWhiteList(url: string): boolean {
  return WHITE_LIST_PATHS.some(p => url.includes(p))
}

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

export const authRequest = axios.create({
  baseURL: AUTH_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

function addTokenInterceptor(instance: axios.AxiosInstance) {
  instance.interceptors.request.use(
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
}

function addResponseInterceptor(instance: axios.AxiosInstance, isAuth: boolean = false) {
  instance.interceptors.response.use(
    (response) => {
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
      if (isAuth) {
        return data.data
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
          const res = await authRequest.post('/refresh', { refreshToken })
          const data = res as any
          setToken(data.accessToken)
          setRefreshToken(data.refreshToken)
          originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
          pendingRequests.forEach((cb) => cb(data.accessToken))
          pendingRequests = []
          return request(originalRequest)
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
}

addTokenInterceptor(request)
addTokenInterceptor(authRequest)
addResponseInterceptor(request, false)
addResponseInterceptor(authRequest, true)

export default request
