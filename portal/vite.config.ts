import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: '/portal/',
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api/auth': {
        target: 'https://kb.marschat.online',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/auth/, '/kb/api/auth')
      },
      '/api/portal': {
        target: 'https://main.marschat.online',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/portal/, '/portal')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
