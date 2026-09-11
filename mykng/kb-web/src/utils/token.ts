/**
 * Token 存储 —— **薄适配层**（Phase 6 组件收敛）
 *
 * 实现已全部收敛到公共组件 `@marschat/auth-components`，本文件只剩两件事：
 * 1. 绑定本应用的 localStorage key（保持 `kb_*` 前缀，不动老用户的登录态）；
 * 2. 原样转发导出，让上层既有 import 路径不变。
 *
 * ⚠️ 这里**不再**自己写 localStorage 读写逻辑。历史上 kb-web / kb-ops-web /
 * infra-monitor-web 各抄了一份、key 前缀还各不相同，改一处要改三处（甚至漏改）。
 */
import { initTokenConfig } from '@marschat/auth-components'

initTokenConfig({
  accessTokenKey: 'kb_access_token',
  refreshTokenKey: 'kb_refresh_token',
  tokenKindKey: 'kb_token_kind',
  // 统一登出（SLO）要用 id_token 作 id_token_hint
  idTokenKey: 'kb_id_token',
})

export {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  getIdToken,
  setIdToken,
  removeIdToken,
  clearTokens,
  getTokenKind,
  setTokenKind,
  isOidcToken,
} from '@marschat/auth-components'

export type { TokenKind } from '@marschat/auth-components'
