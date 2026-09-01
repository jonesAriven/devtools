import { execSync } from 'node:child_process'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 构建指纹：注入到前端运行时（window 全局 + Console 横幅）。
// 用途：一眼确认浏览器里跑的是哪一次构建的产物 —— 排查「改了没生效 / 拿到旧包」的第一道闸。
// 构建发生在 mykng 宿主机（deploy.sh 里 npm run build），那里是 git clone，git 可用。
function buildInfo() {
  let commit = 'unknown'
  let branch = 'unknown'
  try {
    commit = execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim()
  } catch { /* 构建环境无 .git（如 docker build 只 COPY 源码）时降级为 unknown */ }
  try {
    branch = execSync('git rev-parse --abbrev-ref HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim()
  } catch { /* 同上 */ }
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return {
    commit,
    branch,
    time: `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`,
  }
}

export default defineConfig({
  plugins: [vue()],
  base: '/',
  define: {
    __BUILD_INFO__: JSON.stringify(buildInfo()),
  },
  server: {
    proxy: { '/api': 'http://127.0.0.1:8311' }
  }
})
