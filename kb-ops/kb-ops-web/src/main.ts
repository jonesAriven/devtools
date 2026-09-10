import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { getToken } from '@/utils/token'

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
}
