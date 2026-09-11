import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { getToken } from '@/utils/token'
import { startSessionWatcher } from '@/utils/sso'

import './styles/index.scss'

const app = createApp(App)
const pinia = createPinia()

for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')

if (getToken()) {
  // TODO: 启动时获取用户信息
  // Phase 6：启动会话监视 —— 任一应用统一登出后，本应用会随之退出（跨应用单点登出联动）
  startSessionWatcher()
}
