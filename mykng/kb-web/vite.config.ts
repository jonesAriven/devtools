import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const ctx = env.VITE_CONTEXT_PATH || '/kb'

  return {
    base: `${ctx}/s/`,
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
      port: 3000,
      proxy: {
        [`${ctx}/api`]: {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      target: 'es2015',  // 现代浏览器目标，跳过不必要的 polyfill
      chunkSizeWarningLimit: 1600,  // wangeditor 单体 ~1.6MB，无法再拆
      rollupOptions: {
        output: {
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'axios': ['axios'],
            'wangeditor': ['@wangeditor/editor', '@wangeditor/editor-for-vue'],
            // 以下几个已在业务代码里改成 await import() 动态加载
            // 不在 manualChunks 里命名，避免被 Vite 首屏 modulepreload
            'xlsx': ['xlsx'],
            'dompurify': ['dompurify'],
          },
        },
      },
    },
  }
})
