import axios from 'axios'
import type { R } from '@/types'
import { getToken, getRefreshToken, setToken, setRefreshToken, clearTokens, isOidcToken } from '@/utils/token'
import { refreshOidcToken } from '@/utils/sso'
import { ElMessage } from 'element-plus'
import router from '@/router'

const ctx = import.meta.env.VITE_CONTEXT_PATH || '/kb'

const WHITE_LIST_PATHS = ['/auth/login', '/auth/refresh', '/share/verify/', '/share/detail/']

function isWhiteList(url: string): boolean {
  return WHITE_LIST_PATHS.some(p => url.includes(p))
}

const request = axios.create({
  baseURL: `${ctx}/api`,
  timeout: 8000,
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
      const msg = data.message || '请求失败'
      // 可观测性：仅凭 toast「请求失败」无法定位是哪个请求。
      // 注意：落到这里的典型场景是「HTTP 200 但响应体不是业务信封」
      //（多为请求打到了没有后端路由的路径，被网关 SPA 兜底 /kb/** 返回 index.html）。
      console.error('[api] 业务响应异常', {
        url: `${ctx}/api${response.config.url ?? ''}`,
        code: (data as any)?.code,
        message: msg,
        traceId: (data as any)?.traceId,
        bodyPreview: typeof response.data === 'string' ? response.data.slice(0, 160) : undefined,
      })
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
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
        // 双 token 体系分流：OIDC（auth-center RS256）走 SAS 静默续期，legacy 走原 /auth/refresh
        if (isOidcToken()) {
          await refreshOidcToken()
          const newToken = getToken()
          if (!newToken) throw new Error('OIDC 续期后无 token')
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          pendingRequests.forEach((cb) => cb(newToken))
          pendingRequests = []
          return request(originalRequest)
        }
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
    // 可观测性：HTTP 层失败（含 5xx）记录 URL/状态码/traceId，便于事后定位
    console.error('[api] 请求失败', {
      url: `${ctx}/api${originalRequest?.url ?? ''}`,
      status: error.response?.status,
      message,
      traceId: error.response?.data?.traceId,
    })
    if (!isWhiteList(url)) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
