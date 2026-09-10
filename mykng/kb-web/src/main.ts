import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { getToken } from '@/utils/token'
import { useModuleStore } from '@/stores/module'
import { useAppStore } from '@/stores/app'
import { setupErrorHandler } from '@/utils/errorReporter'

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
}
