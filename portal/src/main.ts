import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/index.scss'

// Element Plus 按需引入：模板组件由 unplugin-vue-components 自动导入
// 只显式导入命令式 API 的样式
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-loading.css'
import 'element-plus/theme-chalk/el-notification.css'

// Element Plus 图标 - 按需注册（只注册项目实际用到的 14 个）
import {
  ArrowDown,
  Back,
  Delete,
  Document,
  Download,
  Edit,
  InfoFilled,
  Key,
  Lightning,
  Lock,
  Menu,
  Plus,
  Position,
  Refresh,
  Setting,
  Star,
  StarFilled,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'

const app = createApp(App)
const pinia = createPinia()

const icons = {
  ArrowDown,
  Back,
  Delete,
  Document,
  Download,
  Edit,
  InfoFilled,
  Key,
  Lightning,
  Lock,
  Menu,
  Plus,
  Position,
  Refresh,
  Setting,
  Star,
  StarFilled,
  SwitchButton,
  User,
}
for (const [name, comp] of Object.entries(icons)) {
  app.component(name, comp)
}

app.use(pinia)
app.use(router)
app.mount('#app')
