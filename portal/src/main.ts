import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { useUserStore } from '@/stores/user'
import { startSessionWatcher, bffAuthorizeUrl } from '@/utils/sso'

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
//
// ⚠️ 必须显式注入 getToken / clearLocalAuth：
//   portal 的凭据是**自家服务端换票后写入 `portal_token`**（见 stores/user.ts 的 TOKEN_KEY），
//   并不是组件库约定的 `auth_access_token` 键。而监视器默认只用组件库的键判断「本地还有没有凭据」，
//   读不到就在 tick 首行短路、**永不发出探针** → 别处登出后 portal 永远登不掉。
//   （2026-09-12 实测：运行期 0 次 `/auth/session` 探针，只能靠 401 拦截器被动跳 ?reauth=1。）
const userStore = useUserStore(pinia)
if (userStore.token) {
  startSessionWatcher({
    getToken: () => userStore.token,
    clearLocalAuth: () => userStore.clearSession(),
    // 身份一致性守卫（auth-components 0.5.4+）：exchange 响应带 authUid（auth-center 用户 id），
    // 与探针返回的 username（同为 auth uid）比对，错位（共享浏览器换人）→ 清本地后重走 BFF 换票
    getLocalIdentity: () => userStore.authUid || null,
    onIdentityMismatch: () => {
      window.location.href = bffAuthorizeUrl(window.location.origin)
    },
  })
}
