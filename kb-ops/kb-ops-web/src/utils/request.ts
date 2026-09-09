import { createRequest, createLocalStorageTokenStore } from '@marschat/frontend-common'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { API_BASE_URL, AUTH_BASE_URL } from '@/config'
import { isOidcToken, refreshOidcToken } from '@/utils/sso'
import { getToken, clearTokens } from '@/utils/token'

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
      // OIDC token 尝试静默续期
      if (isOidcToken()) {
        try {
          await refreshOidcToken()
          return // 续期成功，让请求库自动重放
        } catch {
          // 续期失败，走登出流程
        }
      }
      // legacy token 或 OIDC 续期失败，清除并跳转登录页
      clearTokens()
      router.push('/login')
    },
  },
})

export { authRequest }
export default request
