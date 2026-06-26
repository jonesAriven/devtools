import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  base: '/kb/s/',
  build: {
    sourcemap: 'hidden',
    outDir: 'dist',
  },
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/kb/api': {
        target: 'http://192.168.31.105:8090',
        changeOrigin: true,
        // 解决后端 Connection: close 导致 Node HTTP parser 报错
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Connection', 'keep-alive')
          })
          proxy.on('error', (err, _req, res) => {
            console.log('[proxy error]', err.message)
            if (res && !res.headersSent) {
              res.writeHead(502, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({ code: 502, message: '代理请求失败', data: null }))
            }
          })
        },
      },
    },
  },
})
