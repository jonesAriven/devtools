import { createRequest, createLocalStorageTokenStore } from '@marschat/request'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { API_BASE_URL, AUTH_BASE_URL } from '@/config'

/**
 * 统一 axios 实例工厂（@marschat/request）。
 *
 * 迁移前本文件是全仓最成熟的 request 实现，已上抽为公共包 @marschat/request；
 * 此处只保留应用侧差异：baseURL、token 存储 key 前缀、UI 反馈（ElMessage / router）。
 * 行为与迁移前一致：业务实例返回完整 response，auth 实例解包 data.data，
 * 401 走 /refresh 并发队列重放，白名单 /login、/refresh 不弹错、不跳登录。
 */
const { request, authRequest } = createRequest({
  baseURL: API_BASE_URL,
  authBaseURL: AUTH_BASE_URL,
  tokenStore: createLocalStorageTokenStore('kb_ops'),
  hooks: {
    onError: (message) => ElMessage.error(message),
    onUnauthorized: () => router.push('/login'),
  },
})

export { authRequest }
export default request
