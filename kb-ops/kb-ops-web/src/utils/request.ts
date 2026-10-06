import { createRequest, createLocalStorageTokenStore } from '@marschat/frontend-common'
import { ElMessage } from 'element-plus'
import router from '@/router'
import {
  API_BASE_URL,
  AUTH_BASE_URL,
  sso,
  currentSpaPath,
  clearTokens,
  isOidcToken,
} from '@/config'

/**
 * 统一 axios 实例（工厂在 `@marschat/frontend-common`）。
 * 本文件只保留**应用侧差异**：baseURL、token 存储 key 前缀、UI 反馈（ElMessage / router）。
 * 业务实例返回完整 response，auth 实例解包 data.data；白名单 /login、/refresh 不弹错、不跳登录。
 *
 * 🔴 2026-10-05 修复（Phase 13 迁移时发现）：此前写成
 *    `createRequest({ hooks: { onError, onUnauthorized } })`，但
 *    `frontend-common@0.3.5` 的 `CreateRequestOptions` **没有 `hooks` 字段**
 *    （已发布产物中 `hooks` 出现 **0 次**）→ 两个回调被**静默忽略**，落到默认实现
 *    `onUnauthorized = () => { clearTokens(); location.href = appPath('/login') }`。
 *    后果：下面的 OIDC 静默续期分流**从未执行**（死代码），OIDC 会话过期一律硬踢登录页。
 *    现按正确签名放到**顶层**。
 */
const { request, authRequest } = createRequest({
  baseURL: API_BASE_URL,
  authBaseURL: AUTH_BASE_URL,
  tokenStore: createLocalStorageTokenStore('kb_ops'),
  onError: (message: string) => ElMessage.error(message),
  onUnauthorized: async () => {
    // ── 分流 1：OIDC（public client，无 refresh_token）→ 静默重授权 ──
    // ⚠️ 必须放在 legacy 清本地/跳登录**之前**：SAS 不给 public client 签发
    //    refresh_token，OIDC 用户必然没有 refresh_token —— 若先清本地再跳登录，
    //    静默重授权永远走不到。renew 会导航离开，IdP 会话在则无感续期。
    if (isOidcToken()) {
      // 回跳必须传**路由内路径**（currentSpaPath 已剥离 /ops 前缀）：原样传 pathname
      // 会在 sso-callback 的 router.replace(base) 再拼一次 → /ops/ops/... 落 404（2026-09-14 实测）
      await sso.renew(currentSpaPath())
      return // 已导航离开；若回跳到登录页则交由路由守卫处理
    }
    // ── 分流 2：legacy（本地登录态，无 refresh 端点）→ 清本地 + 跳登录 ──
    clearTokens()
    void router.push('/login')
  },
})

export { authRequest }
export default request
