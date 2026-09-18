import axios, { type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import { isOidcToken } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { CONTEXT_PATH } from '@/config/runtime'
import { renewOidcSession, isReauthInFlight } from '@/utils/sso'

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

// ==========================================================================
// D-1（2026-09-18）：401 续期语义按会话渠道分流
//
// 目标：把「一次真 HTTP 401 ⇒ 无差别硬跳登录页」改成「先按会话渠道分流」——
//   · OIDC 会话（浏览器侧 IdP 会话可能仍在）→ 静默重授权，用户无感恢复；
//   · legacy 会话（账密 / 邮箱码，浏览器侧无 IdP 会话）→ 回登录页（与今日一致）。
// （业务 `body code:401` 走**成功分支**只 reject，不在此触发；真 401 的唯一发射点是
//   后端 JwtInterceptor。）
// ==========================================================================

// 登录/换票自身路径：绝不触发续期，防递归（I-D1-3）
const AUTH_WHITELIST = ['/auth/sso/', '/auth/login', '/auth/mail-login', '/auth/slo', '/auth/session']
const isAuthWhitelist = (url: string) => AUTH_WHITELIST.some(p => (url || '').includes(p))

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
      // 业务 code:401 走**成功分支**只 reject（P1）：真 HTTP 401 才在下方错误分支触发续期
      return Promise.reject(new Error(result?.message || '请求失败'))
    },
    async (error) => {
      const userStore = useUserStore()
      const cfg = error.config || {}
      const url: string = cfg.url || ''
      if (error.response?.status === 401) {
        // ★ 渠道必须在 clearSession() **之前**判定：
        //   stores/user.ts 的 clearSession() 会把 portal_token_kind 一并 remove（P5），
        //   读晚了就永远读不到 → OIDC 分支会退化成硬跳登录页。
        const oidc = isOidcToken()
        // 可观测性（D-3 预埋，零风险）：跳转前落一条带 url / hadToken / 渠道的日志
        // ⚠️ 用 warn 而非 error：error 级会被错误监控采集，且浏览器验收里有「console 零报错」一项
        console.warn('[api] 401', { url, hadToken: !!userStore.token, kind: oidc ? 'oidc' : 'legacy' })

        // 白名单 / 已处理过 ⇒ 只 reject，不再导航（防递归、防并发多跳，I-D1-2/I-D1-3）
        if (isAuthWhitelist(url) || cfg._retry) {
          return Promise.reject(error)
        }
        cfg._retry = true

        if (oidc) {
          // ── OIDC 会话：IdP 会话在 → 静默重授权秒回，用户无感；真没了 → 落登录页 ──
          // 单飞：已有续期在途（守卫或另一次 401 发起）⇒ 不再跳（I-D1-1）
          if (isReauthInFlight()) return Promise.reject(error)
          // 只清本地，**绝不**调 userStore.logout()：SLO 会销毁全局 IdP 会话 → 静默免登失效（I-D1-5）
          userStore.clearSession()
          ElMessage.warning('登录态已刷新，正在恢复…')
          // 只传 origin：portal 是 BFF 服务端流，redirect 的路径部分会被
          // SsoController#normalizeOrigin 丢弃，且 SsoCallbackView 固定 replace('/')
          // —— 续期后一律回首页（详见 utils/sso.ts 的 renewOidcSession 文档注释）
          const nav = await renewOidcSession(window.location.origin)
          if (!nav) {
            // IdP 会话已不在 / 探针异常 → 降级回登录页（reauthInFlight 已在 renewOidcSession 内复位）
            ElMessage.error('登录已过期，请重新登录')
            window.location.href = `${CONTEXT_PATH}/login?reauth=1`
          }
          return Promise.reject(error)
        }

        // ── legacy 会话（账密 / 邮箱码）：浏览器侧无 IdP 会话，只能回登录页（与今日一致）──
        userStore.clearSession()
        ElMessage.error('登录已过期，请重新登录')
        // reauth=1 供登录页识别这是一次"过期重认证"（登录页仍会探一次 IdP 会话，有则免登送回）
        // CONTEXT_PATH 而非硬编码 '/portal'：部署 base 由 app-config.json 注入（T7 收敛）
        window.location.href = `${CONTEXT_PATH}/login?reauth=1`
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
