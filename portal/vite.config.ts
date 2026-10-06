import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dts: 'src/components.d.ts',
    }),
  ],
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
      },
      // Phase 13：装配层的 `permissionsIssuer: '/portal/api'` 指向 portal-server 同源代理。
      // dev 态下 vite dev server（:3000）没有这条转发，权限请求会 404 ⇒ configured=false
      // ⇒ 权限体系静默全放行（菜单全出来、按钮全可点，不报任何错）。
      // `/api/auth` 那条规则指向的是 **kb.marschat.online**（不是 portal-server），对本代理无效，
      // 故这里显式补一条到 portal-server 本机端口。
      // ⚠️ 零生产影响：仅 dev server 生效；生产由 nginx 同源转发 /portal/api。
      '/portal/api': {
        target: 'http://localhost:8087',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    target: 'es2015',
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'axios': ['axios'],
        },
      },
    },
  },
})
