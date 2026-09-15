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
import { permissions } from '@/utils/permissions'
import { CONTEXT_PATH } from '@/config'

// ⚠️ 统一声明本应用的**部署 base（子路径）**，供公共库在「跳登录页」时拼出带 base 的地址。
//    背景：公共库若写死「根相对 /login」，会跳到 https://kb.marschat.online/login
//    → nginx 404（本 SPA 实际在 /ops 下）。见 ADR §32.11。
window.__MARSCHAT_APP_BASE__ = CONTEXT_PATH

import './styles/index.scss'

const app = createApp(App)
const pinia = createPinia()

for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

const bootToken = getToken()
if (bootToken) {
  // TODO: 启动时获取用户信息
  // 🔴 2026-09-15 回归修复：**只有 OIDC 会话才启动会话监视器**（与 infra-monitor 同修法）。
  //
  // 监视器探的是 auth-center `/auth/session`（IdP 会话），而账密/邮箱码登录得到的是
  // 本应用自签 token（`token_kind=legacy`），浏览器里**本就没有 IdP 会话** →
  // 探针恒返回 `authenticated:false` → 组件默认 `confirmCount:1` + `redirectOnLost:true`，
  // 且 `start()` 内 `setTimeout(probe, 3000)` 会在 **3 秒后首探** →
  // 每次整页刷新（F5 / 直输网址 / 从门户跳回）都被判「他处已登出」→
  // `clearLocalAuth()` + 跳 `<base>/login?slo=1`，账密会话活不过 3 秒。
  //
  // 对无 IdP 会话的登录方式做 SLO 联动本无意义（没有会话可监视），故按 token 类型分流。
  if (isOidcToken(bootToken)) {
    // Phase 6：启动会话监视 —— 任一应用统一登出后，本应用会随之退出（跨应用单点登出联动）
    startSessionWatcher()
  }
  // Phase 2：预取 RBAC 权限点（供菜单过滤 / PermissionGate 消费；路由守卫侧也会 ensure）。
  // 拉取失败一律降级为 configured=false（全放行），绝不阻塞启动。
  void permissions.ensure()
}
