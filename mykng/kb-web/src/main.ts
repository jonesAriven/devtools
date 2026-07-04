import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { getToken } from '@/utils/token'
import { useModuleStore } from '@/stores/module'

// Element Plus 按需导入：仅导入命令式 API 的样式（模板组件由 unplugin-vue-components 自动按需导入）
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-notification.css'
import 'element-plus/theme-chalk/el-loading.css'

import './styles/index.scss'
import './styles/mobile.scss'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.mount('#app')

// 应用启动时：若已登录，拉取模块状态用于动态菜单（失败时 store 内部降级为全部可用）
if (getToken()) {
  useModuleStore(pinia).fetchModules()
}
