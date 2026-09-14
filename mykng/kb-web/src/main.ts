import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { getToken } from '@/utils/token'
import { startSessionWatcher } from '@/utils/sso'
import { permissions } from '@/utils/permissions'
import { useModuleStore } from '@/stores/module'
import { useAppStore } from '@/stores/app'
import { setupErrorHandler } from '@/utils/errorReporter'
import { CONTEXT_PATH } from '@/config'

// ⚠️ 统一声明本应用的**部署 base（子路径）**，供公共库（frontend-common / auth-components）
//    在需要「跳登录页」时拼出带 base 的正确地址。
//    背景：公共库若写死「根相对 /login」会跳到**域名根**，
//    而本 SPA 部署在 /kb 下 → nginx 无该 location → 404（2026-09-14 实测，见 ADR §32.11）。
window.__MARSCHAT_APP_BASE__ = CONTEXT_PATH

setupErrorHandler()

import './styles/index.scss'
import './styles/mobile.scss'
import './styles/dark.scss'

const app = createApp(App)
const pinia = createPinia()

for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')

// 应用启动时：初始化主题（暗黑/明亮）
useAppStore(pinia).initThemeOnBoot()

// 应用启动时：若已登录，拉取模块状态用于动态菜单（失败时 store 内部降级为全部可用）
if (getToken()) {
  useModuleStore(pinia).fetchModules()
  // Phase 6：启动会话监视 —— 任一应用统一登出后，本应用会随之退出（跨应用单点登出联动）
  startSessionWatcher()
  // Phase 2：预取 RBAC 权限点（供菜单过滤 / PermissionGate 消费；路由守卫侧也会 ensure）。
  // 拉取失败一律降级为 configured=false（全放行），绝不阻塞启动。
  void permissions.ensure()
}
