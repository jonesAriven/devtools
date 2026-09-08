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
      // 本地开发时指向 marschat-components 源码（从 devtools/portal 到 ideaworkspace/marschat-components）
      '@marschat/auth-components': resolve(__dirname, '../../marschat-components/packages/auth-components/src'),
      '@marschat/frontend-common': resolve(__dirname, '../../marschat-components/packages/frontend-common/src'),
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
