import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 官方暗色变量包，与本项目 theme.css 共用同一个 html.dark 开关
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/theme.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { logBoot } from './utils/navLog'

// 构建指纹打在 Console 第一行：确认浏览器里跑的是哪一次构建的产物
logBoot()

// ── 主题：默认跟随系统，用户可手动覆盖并存 localStorage ──
const THEME_KEY = 'cosmic-theme'
const mq = window.matchMedia('(prefers-color-scheme: dark)')

function applyTheme() {
  const stored = localStorage.getItem(THEME_KEY)
  const dark = stored ? stored === 'dark' : mq.matches
  document.documentElement.classList.toggle('dark', dark)
  return dark
}
applyTheme()
// 未手动锁定时，跟随系统切换实时生效
mq.addEventListener('change', () => {
  if (!localStorage.getItem(THEME_KEY)) applyTheme()
})
// 供「系统管理 / 顶栏」将来加主题按钮时使用
window.__cosmicSetTheme = (mode) => {
  if (mode) localStorage.setItem(THEME_KEY, mode)
  else localStorage.removeItem(THEME_KEY)
  return applyTheme()
}

const app = createApp(App)
app.use(ElementPlus, { locale: zhCn })
for (const [name, comp] of Object.entries(Icons)) app.component(name, comp)
app.use(router)
app.config.errorHandler = (err, vm, info) => {
  document.title = 'UIERR: ' + (err?.message || String(err)) + ' @ ' + info
  console.error(err, info)
}
window.addEventListener('unhandledrejection', e => {
  document.title = 'UIREJ: ' + (e.reason?.message || String(e.reason))
})
app.mount('#app')
