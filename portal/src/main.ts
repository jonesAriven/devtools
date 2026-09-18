import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { useUserStore } from '@/stores/user'
// T7 组件收敛（2026-09-15）：部署 base 取自运行时配置（app-config.json ← apps-registry.yml），
// 不再各处硬编码 '/portal'（此前 main/router/sso/permissions/两个视图共 6 处各写一份）
import { CONTEXT_PATH } from '@/config/runtime'

// ⚠️ 统一声明本应用的**部署 base（子路径）**，供公共库在「跳登录页」时拼出带 base 的地址
//    （写死 '/login' 会跳到域名根 → nginx 404）。portal 的 router base 同源于 CONTEXT_PATH。
window.__MARSCHAT_APP_BASE__ = CONTEXT_PATH
import { startSessionWatcher, renewOidcSession } from '@/utils/sso'
import { permissions } from '@/utils/permissions'
import { isOidcToken } from '@marschat/auth-components'

// Element Plus 图标 - 全量注册
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)
const pinia = createPinia()

// 全局注册所有 Element Plus 图标
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

// Phase 6：已登录则启动会话监视 —— 任一应用统一登出后，本应用随之退出（跨应用单点登出联动）
//
// ⚠️ 必须显式注入 getToken / clearLocalAuth：
//   portal 的凭据是**自家服务端换票后写入 `portal_token`**（见 stores/user.ts 的 TOKEN_KEY），
//   并不是组件库约定的 `auth_access_token` 键。而监视器默认只用组件库的键判断「本地还有没有凭据」，
//   读不到就在 tick 首行短路、**永不发出探针** → 别处登出后 portal 永远登不掉。
//   （2026-09-12 实测：运行期 0 次 `/auth/session` 探针，只能靠 401 拦截器被动跳 ?reauth=1。）
const userStore = useUserStore(pinia)
if (userStore.token) {
  // 🔴 2026-09-15 回归修复（Phase 11）：**只有 SSO（OIDC）会话才启动会话监视器**。
  //
  // 监视器探的是 auth-center `/auth/session`（**IdP 会话**）；而账密 / 邮箱验证码走的是
  // portal-server 的 **BFF 换票**——auth-center 的 Set-Cookie 落在后端进程里，
  // **浏览器侧根本没有 IdP 会话** → 探针恒返回 `authenticated:false`。
  // 组件默认 `confirmCount:1` + `redirectOnLost:true`，且 `start()` 会在 **3 秒后首探**，
  // 于是每次整页刷新（F5 / 直输网址 / 从门户跳回）都被判「他处已登出」→
  // `clearSession()` + 跳 `/portal/login?slo=1`，账密会话活不过 3 秒。
  //
  // 渠道由 stores/user.ts 的 setSession(kind) 打标（键 `portal_token_kind`）：
  // ssoExchange → 'oidc'；login / 邮箱码 → 'legacy'。
  // 同款写法见 infra-monitor-web/src/main.ts（devtools 59343a4d）。
  if (isOidcToken()) {
    startSessionWatcher({
      getToken: () => userStore.token,
      clearLocalAuth: () => userStore.clearSession(),
      // 身份一致性守卫（auth-components 0.5.4+）：exchange 响应带 authUid（auth-center 用户 id），
      // 与探针返回的 username（同为 auth uid）比对，错位（共享浏览器换人）→ 清本地后重走 BFF 换票
      getLocalIdentity: () => userStore.authUid || null,
      // D-1（2026-09-18）：守卫与 401 续期共用同一个 renewOidcSession()（同一 reauthInFlight 单飞），
      // 两条路径互斥、不叠加跳转。
      onIdentityMismatch: () => { void renewOidcSession(window.location.origin) },
    })
  }
  // Phase 2：预取 RBAC 权限点（走 portal-server 的 BFF 代理；路由守卫侧也会 ensure）。
  // 拉取失败一律降级为 configured=false（全放行），绝不阻塞启动。
  void permissions.ensure()
}
