import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// Element Plus 按需导入：仅导入命令式 API 的样式（模板组件由 unplugin-vue-components 自动按需导入）
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-notification.css'
import 'element-plus/theme-chalk/el-loading.css'

import './styles/index.scss'
import './styles/mobile.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
