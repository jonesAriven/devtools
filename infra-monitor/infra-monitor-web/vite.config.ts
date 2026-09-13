import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const ctx = env.VITE_CONTEXT_PATH || '/infra'

  return {
    base: `${ctx}/`,
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
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    server: {
      port: 3002,
      proxy: {
        [`${ctx}/infra-api`]: {
          target: 'http://localhost:8088',
          changeOrigin: true,
          rewrite: (path) => path.replace(new RegExp(`^${ctx}/infra-api`), '/infra'),
        },
      },
    },
    build: {
      target: 'es2015',
      chunkSizeWarningLimit: 1600,
      // 2026-09-13：assets 路径错配 404 曾被浏览器以 immutable 缓存 7 天（URL 未变白屏）
      // 换 assets 目录名一次性绕开坏缓存；后续部署窗口期靠 nginx /infra/ html no-cache 最小化
      assetsDir: 'assets-v2',
      rollupOptions: {
        output: {
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'axios': ['axios'],
          },
        },
      },
    },
  }
})
