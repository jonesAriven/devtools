import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { getToken } from '@/utils/token'
import { startSessionWatcher } from '@/utils/sso'
import { permissions, setupAuthGuard } from '@/utils/permissions'

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
app.use(ElementPlus)
app.mount('#app')

// Phase 6：已登录则启动会话监视 —— 任一应用统一登出后，本应用随之退出（跨应用单点登出联动）
if (getToken()) {
  startSessionWatcher()
  // Phase 2：预取 RBAC 权限点（供菜单过滤 / PermissionGate 消费；路由守卫侧也会 ensure）。
  // 拉取失败一律降级为 configured=false（全放行），绝不阻塞启动。
  void permissions.ensure()
}
