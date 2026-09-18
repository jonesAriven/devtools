import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { getToken, isOidcToken } from '@/utils/token'
import { startSessionWatcher } from '@/utils/sso'
import { permissions, setupAuthGuard } from '@/utils/permissions'
import { CONTEXT_PATH } from '@/config'

// ⚠️ 统一声明本应用的**部署 base（子路径）**，供公共库在「跳登录页」时拼出带 base 的地址
//    （写死 '/login' 会跳到域名根 → nginx 404）。见 ADR §32.11。
window.__MARSCHAT_APP_BASE__ = CONTEXT_PATH

import './styles/index.scss'

const app = createApp(App)
const pinia = createPinia()

for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

// Phase 5：路由权限守卫——必须在 app.use(router) 之前注册（先于首次导航，
// 挂载路由前先拉权限，修正「先挂路由后拉权限」脆弱点；未声明 meta.perm 的路由不受管）
setupAuthGuard(router)

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

// Phase 6：已登录则启动会话监视 —— 任一应用统一登出后，本应用随之退出（跨应用单点登出联动）
const bootToken = getToken()
if (bootToken) {
  // 🔴 2026-09-15 回归修复（Phase 11）：**只有 OIDC 会话才启动会话监视器**。
  //
  // 监视器探的是 auth-center `/auth/session`（IdP 会话），账密/邮箱码登录走的是本应用 BFF，
  // 浏览器里**本就没有 IdP 会话** → 探针恒返回 `authenticated:false` → 组件默认
  // `confirmCount:1` + `redirectOnLost:true`，且 `start()` 会在 **3 秒后首探**，
  // 于是每次整页刷新（F5 / 直输网址 / 从门户跳回）都被判「他处已登出」→
  // `clearLocalAuth()` + 跳 `/infra/login?slo=1`，账密会话活不过 3 秒。
  //
  // 线上实测（Edge headless + CDP）：账密登录后本地 `token_kind=legacy`、
  // `/auth/session` = `{authenticated:false}`，整页刷新后 **3.0s** 落 `/infra/login?slo=1`。
  // 对这类会话做 SLO 联动本无意义（无 IdP 会话可监视），故按 token 类型分流。
  if (isOidcToken(bootToken)) {
    startSessionWatcher()
  }
  // Phase 2：预取 RBAC 权限点（供菜单过滤 / PermissionGate 消费；路由守卫侧也会 ensure）。
  // 拉取失败一律降级为 configured=false（全放行），绝不阻塞启动。
  void permissions.ensure()
}
