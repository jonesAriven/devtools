import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { getToken } from '@/utils/token'
import { startSessionWatcher } from '@/utils/sso'
import { permissions } from '@/utils/permissions'

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

if (getToken()) {
  // TODO: 启动时获取用户信息
  // Phase 6：启动会话监视 —— 任一应用统一登出后，本应用会随之退出（跨应用单点登出联动）
  startSessionWatcher()
  // Phase 2：预取 RBAC 权限点（供菜单过滤 / PermissionGate 消费；路由守卫侧也会 ensure）。
  // 拉取失败一律降级为 configured=false（全放行），绝不阻塞启动。
  void permissions.ensure()
}
