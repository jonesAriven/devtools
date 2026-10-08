import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { setTokenKind } from '@marschat/auth-components'
import { login as loginApi, logout as logoutApi, ssoExchangeApi, type LoginRequest } from '@/api/auth'
import { logout as ssoLogoutPortal } from '@/config/session'

const TOKEN_KEY = 'portal_token'
const USER_KEY = 'portal_user'
const ROLE_KEY = 'portal_role'
// auth-center 用户 id：会话监视器「身份一致性守卫」的本地身份依据（0.5.4+）
const AUTH_UID_KEY = 'portal_auth_uid'

/**
 * 登录渠道标记（组件库 `token_kind` 约定，同族：infra_token_kind）。
 *
 * ★ 为什么必须区分渠道：会话监视器探的是 auth-center `/auth/session`（**IdP 会话**），
 *   而 portal 的账密 / 邮箱验证码走的是 **portal-server BFF 换票**——auth-center 的
 *   Set-Cookie 落在后端进程里，**浏览器侧根本没有 IdP 会话**。若对这类会话也启动监视器，
 *   探针恒返回 `authenticated:false` → 组件默认 `confirmCount:1` + `redirectOnLost:true`
 *   且 `start()` 会在 3 秒后首探 → 每次整页刷新都被判「他处已登出」→ 跳 `?slo=1`。
 *   只有浏览器侧真走过 OIDC 授权跳转的会话才值得监视。
 */
/**
 * 登录渠道标记（组件库 `token_kind` 约定）的存储键。
 *
 * Phase 13：此处原有`initTokenConfig({ tokenKindKey })`，**已删除** ——
 * 令牌键绑定改由装配层 `createMarschatApp` 统一完成（它内部调`initTokenConfig(config.tokenKeys)`），
 * 本store 不再是第二个配置源。
 *原写法并未把四键重置（组件实现是 `{...DEFAULT, ...options}` 对象展开，未传的键保留默认值），
 * 删除的收益是**消除重复绑定**、避免两处配置漂移。
 *
 * 该键的**值**不变：由装配层按 `tokenKeyPrefix` 派生出 `portal_token_kind`，
 * 与此前的硬编码完全一致。
 */
const TOKEN_KIND_KEY = 'portal_token_kind'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const username = ref<string>(localStorage.getItem(USER_KEY) || '')
  const role = ref<string>(localStorage.getItem(ROLE_KEY) || 'user')
  const authUid = ref<string>(localStorage.getItem(AUTH_UID_KEY) || '')

  const isLoggedIn = computed(() => !!token.value)
  // ⚠️ 必须同时认 superadmin：auth-center 的超管账号（user.role='superadmin'，§22.1）
  //    是平台唯一的最高权限账号；只比 'admin' 会把超管挡在 portal 用户管理之外
  //    （2026-09-13 浏览器实测：portal_role=superadmin → /users 被重定向回工作台，
  //      BFF /portal/api/admin/users 返回 403「需要管理员权限」）。
  const isAdmin = computed(() => role.value === 'admin' || role.value === 'superadmin')

  /**
   * 写入会话。
   *
   * @param kind 登录渠道 —— `'oidc'` 仅由 SSO 回调（`ssoExchange`）传入；
   *             账密 / 邮箱验证码默认 `'legacy'`（浏览器侧无 IdP 会话）。
   *             该标记决定 `main.ts` 是否启动会话监视器（SLO 联动）。
   */
  function setSession(
    res: { token?: string; accessToken?: string; username?: string; role?: string; authUid?: string },
    fallbackUsername?: string,
    kind: 'oidc' | 'legacy' = 'legacy'
  ) {
    const tokenVal = res.token || res.accessToken || ''
    token.value = tokenVal
    username.value = res.username || fallbackUsername || ''
    role.value = res.role || 'user'
    authUid.value = res.authUid || ''
    localStorage.setItem(TOKEN_KEY, tokenVal)
    localStorage.setItem(USER_KEY, username.value)
    localStorage.setItem(ROLE_KEY, role.value)
    if (authUid.value) localStorage.setItem(AUTH_UID_KEY, authUid.value)
    else localStorage.removeItem(AUTH_UID_KEY)
    setTokenKind(kind)
  }

  async function login(credentials: LoginRequest) {
    const res = await loginApi(credentials)
    // 账密走 portal-server BFF 换票，浏览器侧没有 IdP 会话 → legacy
    setSession(res as any, credentials.username, 'legacy')
    return res
  }

  async function ssoExchange(code: string, state: string) {
    const res = await ssoExchangeApi(code, state)
    // SSO 是浏览器侧 OIDC 授权跳转，确有 IdP 会话 → oidc（可被会话监视器监视）
    setSession(res as any, undefined, 'oidc')
    return res
  }

  /**
   * 仅清本地会话（**不碰 IdP 会话**）。
   *
   * 供 401 拦截器使用：会话过期时要"清干净再走登录页"，而不能顺手把
   * 全局 IdP 会话也干掉 —— 否则登录页的静默免登就没机会把你免密送回来。
   */
  function clearSession() {
    token.value = ''
    username.value = ''
    role.value = 'user'
    authUid.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(ROLE_KEY)
    localStorage.removeItem(AUTH_UID_KEY)
    // 渠道标记一并清掉：否则登出后残留 'oidc'，下次账密会话可能被误判为可监视
    localStorage.removeItem(TOKEN_KIND_KEY)
  }

  /**
   * 退出登录 —— **统一登出（SLO）**（Phase 6）
   *
   * 旧实现只清 3 个 localStorage key，**IdP 会话仍然活着**，于是再打开
   * portal 或任一兄弟应用都会被静默免登**直接免密登回去** —— "退不掉"。
   * 现在交给 auth-center `/auth/slo`：清 SSO Cookie → 销毁 IdP 会话 → 回跳登录页。
   * ⚠️ 会导航离开，调用方不要再 router.push。
   *
   * 🔴 T-ENG-8（2026-10-07）：补上**服务端侧清凭据**这一环。
   *   此前 `@/api/auth` 里虽定义了 `logout()`，但**没有任何调用方**（死代码），
   *   而后端 `/api/auth/logout` 又是空实现 ⇒ 双重缺失：
   *   用户点了退出，portal-server 手里那枚可直调 `/admin/**` 的中心 access_token **原封不动**。
   *   现在登出链路补全为：服务端吊销+清池 → 客户端清本地 → SLO。
   *
   * ⚠️ 服务端调用必须 **best-effort**：失败/401/中心不可达都**不得阻断**登出 ——
   *   "退得掉"是硬需求，不能因为后端抖一下就把用户锁在登录态里。
   *   （`request.ts` 已把 `/auth/logout` 加入 401 白名单，避免与 SLO 跳转抢导航。）
   */
  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 故意吞掉：登出不应被服务端故障卡住（后端自身也是 best-effort 返回 200）
    }
    clearSession()
    ssoLogoutPortal()
  }

  return {
    token,
    username,
    role,
    authUid,
    isLoggedIn,
    isAdmin,
    // 🔴 既存缺陷修复（2026-10-06，Phase 13 迁移期间发现，已被覆盖过一次故补第二次）：
    // `setSession` 定义在本文件第 57 行但**长期漏了导出**，而 `views/LoginView.vue:131`
    // 的邮箱验证码登录路径正在调它 ⇒ 运行期必抛
    // `TypeError: userStore.setSession is not a function` ⇒ **邮箱码登录整条链路不可用**。
    //
    // 经 `git show HEAD:portal/src/stores/user.ts` 确认**迁移前即如此**，非本次迁移引入。
    // ⚠️ 本行曾于 12:26 被一次整体重写覆盖掉（QA 复验时发现），故此处注释务必保留，
    //    下次改本文件时请确认这行仍在。
    setSession,
    login,
    ssoExchange,
    clearSession,
    logout
  }
})
