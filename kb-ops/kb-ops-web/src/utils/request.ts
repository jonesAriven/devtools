import { createRequest, createLocalStorageTokenStore } from '@marschat/frontend-common'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { API_BASE_URL, AUTH_BASE_URL, CONTEXT_PATH } from '@/config'
import { renewByReauthorize } from '@/utils/sso'
import { getToken, clearTokens, isOidcToken } from '@/utils/token'

/**
 * renew 回跳目标 —— 必须是 **router 内部路径**（与 LoginView currentRedirect 同口径）。
 * location.pathname 含部署前缀（/ops），原样传会在 sso-callback 的
 * router.replace(base=/ops) 里再拼一次 → /ops/ops/... 落 404（2026-09-14 实测）。
 */
function currentSpaPath(): string {
  const p = window.location.pathname
  const stripped = p.startsWith(CONTEXT_PATH) ? p.slice(CONTEXT_PATH.length) : p
  return (stripped || '/') + window.location.search
}

/**
 * 统一 axios 实例工厂（@marschat/frontend-common）。
 *
 * 迁移前本文件是全仓最成熟的 request 实现，已上抽为公共包 @marschat/frontend-common；
 * 此处只保留应用侧差异：baseURL、token 存储 key 前缀、UI 反馈（ElMessage / router）。
 * 行为与迁移前一致：业务实例返回完整 response，auth 实例解包 data.data，
 * 401 走 /refresh 并发队列重放，白名单 /login、/refresh 不弹错、不跳登录。
 *
 * SSO OIDC 支持：
 *   - 当 token kind 为 'oidc' 时，401 走 refreshOidcToken() 静默续期
 *   - legacy token 无 refresh 端点，直接登出
 */
const { request, authRequest } = createRequest({
  baseURL: API_BASE_URL,
  authBaseURL: AUTH_BASE_URL,
  tokenStore: createLocalStorageTokenStore('kb_ops'),
  hooks: {
    onError: (message) => ElMessage.error(message),
    onUnauthorized: async () => {
      // ── 分流 1：OIDC（public client，无 refresh_token）→ 静默重授权 ──
      // ⚠️ 必须放在 legacy 清本地/跳登录**之前**：SAS 不给 public client 签发
      //    refresh_token，OIDC 用户必然没有 refresh_token —— 若先清本地再跳登录，
      //    静默重授权永远走不到。renewByReauthorize 会导航离开，会话在则无感续期。
      if (isOidcToken()) {
        // 回跳必须传 router 内部路径：pathname 含部署前缀（/ops），原样传会在
        // sso-callback 的 router.replace(base) 再拼一次 → /ops/ops/... 落 404（2026-09-14 实测）
        await renewByReauthorize(currentSpaPath())
        return // 已导航离开；若回跳到登录页则交由路由守卫处理
      }
      // ── 分流 2：legacy（本地登录态，无 refresh 端点）→ 清本地 + 跳登录 ──
      clearTokens()
      router.push('/login')
    },
  },
})

export { authRequest }
export default request
