import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { useUserStore } from '@/stores/user'
import { startSessionWatcher } from '@/utils/sso'

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
app.use(ElementPlus)
app.mount('#app')

// Phase 6：已登录则启动会话监视 —— 任一应用统一登出后，本应用随之退出（跨应用单点登出联动）
if (useUserStore(pinia).token) {
  startSessionWatcher()
}
