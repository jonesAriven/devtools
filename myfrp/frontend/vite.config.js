import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/frp_manager/',
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/frp_manager/api': {
        target: 'http://localhost:18082',
        changeOrigin: true
      }
    }
  }
})
