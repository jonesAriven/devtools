/**
 * 应用入口（Phase 13 配置化接入）
 *
 * 接入相关的全部工作在 `src/marschat.ts` 的一行装配里完成，本文件只负责
 * 「创建 Vue 应用 + 按正确顺序挂载插件」。
 *
 * 顺序铁律：路由权限守卫已在 `createMarschatApp()` 内注册，因此必须经
 * `marschat.install(app)` 挂 router —— 不能自行 `app.use(router)`，
 * 否则守卫晚于首次导航注册，首屏会跳过权限判定（坑 #5）。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import './styles/index.scss'
import { marschat } from './marschat'

const app = createApp(App)

for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

app.use(createPinia())
marschat.install(app) // 挂 router（守卫已先注册；部署 base 也已由装配层声明）
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

// 会话监视（仅 OIDC 会话启动）+ RBAC 权限预取；拉取失败一律降级为「未配置全放行」，不阻塞启动
marschat.bootstrap()
