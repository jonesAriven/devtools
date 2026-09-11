import axios from 'axios'
import type { R } from '@/types'
import { getToken, getRefreshToken, setToken, setRefreshToken, clearTokens, isOidcToken } from '@/utils/token'
import { renewByReauthorize } from '@/utils/sso'
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
    // 仅当响应体确实是"业务信封"时才做业务码校验。
    // 例外：网关自身的端点（GET /api/system/modules）返回**裸数组**，不带 {code,message,data}，
    // 若不加这个判据，HTTP 200 的正常响应会被误判成"请求失败"弹红条，且调用方只能拿到 reject
    // —— 这正是历史上"请求失败 其实出自成功分支"那类误报的根因（当时被误诊为打到 SPA 兜底路径）。
    // 只豁免数组、不豁免字符串：字符串响应体（典型是被网关 SPA 兜底返回的 index.html）
    // 仍然按异常处理，因为那才是"请求打到了没有后端路由的路径"的真正信号。
    const isBareArray = Array.isArray(response.data)
    if (!isBareArray && data.code !== 0 && data.code !== 200) {
      const msg = data.message || '请求失败'
      const traceId = (data as any)?.traceId
      const url = `${ctx}/api${response.config.url ?? ''}`
      // 可观测性：仅凭 toast「请求失败」无法定位是哪个请求。
      // 注意：落到这里的典型场景是「HTTP 200 但响应体不是业务信封」
      //（多为请求打到了没有后端路由的路径，被网关 SPA 兜底 /kb/** 返回 index.html）。
      console.error('[api] 业务响应异常', {
        url,
        code: (data as any)?.code,
        message: msg,
        traceId,
        bodyPreview: typeof response.data === 'string' ? response.data.slice(0, 160) : undefined,
      })
      // traceId 必须同时出现在「用户看到的提示」和「reject 出的 error」上：
      // - 只打在 console 里 = 用户报障时拿不到，等于没有
      // - 不挂到 error 上 = 调用方 catch 后无法二次拼接，各页面只能自己猜
      const shown = traceId ? `${msg}（traceId: ${traceId}）` : msg
      ElMessage.error(shown)
      return Promise.reject(
        Object.assign(new Error(shown), { traceId, code: (data as any)?.code, url }),
      )
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

      // ── 分流 1：OIDC（auth-center RS256，public client）──
      // ⚠️ 必须放在「有没有 refresh_token」判断**之前**：SAS 3.2.5 对 public client
      //   根本不签发 refresh_token（带 offline_access 会被拒 invalid_scope），
      //   所以 OIDC 用户在这里必然没有 refresh_token —— 若先判断它，就会被当成
      //   "续期凭据都没了"直接弹回登录页，静默重授权永远走不到。
      //   正确做法：静默重授权（IdP 会话还在就秒回新 code，用户无感；会话没了才落登录页）。
      //   renewByReauthorize 会导航离开，故直接返回。
      if (isOidcToken()) {
        originalRequest._retry = true
        await renewByReauthorize(`${window.location.pathname}${window.location.search}`)
        return Promise.reject(error)
      }

      // ── 分流 2：legacy（服务端签发的双 token，走 /auth/refresh）──
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
