/**
 * Token 存储 —— **薄适配层**（Phase 6 组件收敛）
 *
 * 实现已全部收敛到公共组件 `@marschat/auth-components`，本文件只剩两件事：
 * 1. 绑定 kb-ops 的 localStorage key（保持 `kb_ops_` 前缀，不动老用户的登录态）；
 * 2. 原样转发导出，让上层既有 import 路径不变。
 *
 * ⚠️ 历史上 kb-ops 的 token kind 辅助函数（getTokenKind/setTokenKind/isOidcToken）
 * 曾被错误地挂在 `src/utils/sso.ts` 里导出；现统一收归本文件（从组件 re-export），
 * 上层（request.ts）改从 `@/utils/token` 引入即可，import 路径不破。
 */
import { initTokenConfig } from '@marschat/auth-components'

initTokenConfig({
  accessTokenKey: 'kb_ops_access_token',
  refreshTokenKey: 'kb_ops_refresh_token',
  tokenKindKey: 'kb_ops_token_kind',
  // 统一登出（SLO）要用 id_token 作 id_token_hint
  idTokenKey: 'kb_ops_id_token',
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
