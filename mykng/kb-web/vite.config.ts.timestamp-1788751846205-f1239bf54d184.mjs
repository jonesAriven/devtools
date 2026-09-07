// vite.config.ts
import { defineConfig, loadEnv } from "file:///D:/huliang/java/ideaworkspace/devtools/mykng/kb-web/node_modules/.pnpm/vite@5.4.21_sass@1.100.0/node_modules/vite/dist/node/index.js";
import vue from "file:///D:/huliang/java/ideaworkspace/devtools/mykng/kb-web/node_modules/.pnpm/@vitejs+plugin-vue@5.2.4_vi_ae35f9a5e67017358cfeb8e868660bf6/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import AutoImport from "file:///D:/huliang/java/ideaworkspace/devtools/mykng/kb-web/node_modules/.pnpm/unplugin-auto-import@0.17.8_ff1e62a370b6d76932a4589636a9709b/node_modules/unplugin-auto-import/dist/vite.js";
import Components from "file:///D:/huliang/java/ideaworkspace/devtools/mykng/kb-web/node_modules/.pnpm/unplugin-vue-components@0.2_7c0e392478add721e3776a29d8ac34f3/node_modules/unplugin-vue-components/dist/vite.js";
import { ElementPlusResolver } from "file:///D:/huliang/java/ideaworkspace/devtools/mykng/kb-web/node_modules/.pnpm/unplugin-vue-components@0.2_7c0e392478add721e3776a29d8ac34f3/node_modules/unplugin-vue-components/dist/resolvers.js";
import path from "path";
var __vite_injected_original_dirname = "D:\\huliang\\java\\ideaworkspace\\devtools\\mykng\\kb-web";
var vite_config_default = defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const ctx = env.VITE_CONTEXT_PATH || "/kb";
  return {
    base: `${ctx}/s/`,
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ["vue", "vue-router", "pinia"],
        dts: "src/auto-imports.d.ts"
      }),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: "css" })],
        dts: "src/components.d.ts"
      })
    ],
    resolve: {
      alias: {
        "@": path.resolve(__vite_injected_original_dirname, "src")
      }
    },
    server: {
      port: 3e3,
      proxy: {
        [`${ctx}/api`]: {
          target: "http://localhost:8080",
          changeOrigin: true
        }
      }
    },
    build: {
      target: "es2015",
      // 现代浏览器目标，跳过不必要的 polyfill
      chunkSizeWarningLimit: 1600,
      // wangeditor 单体 ~1.6MB，无法再拆
      rollupOptions: {
        output: {
          manualChunks: {
            "vue-vendor": ["vue", "vue-router", "pinia"],
            "axios": ["axios"],
            "wangeditor": ["@wangeditor/editor", "@wangeditor/editor-for-vue"],
            // 以下几个已在业务代码里改成 await import() 动态加载
            // 不在 manualChunks 里命名，避免被 Vite 首屏 modulepreload
            "xlsx": ["xlsx"],
            "dompurify": ["dompurify"]
          }
        }
      }
    }
  };
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJEOlxcXFxodWxpYW5nXFxcXGphdmFcXFxcaWRlYXdvcmtzcGFjZVxcXFxkZXZ0b29sc1xcXFxteWtuZ1xcXFxrYi13ZWJcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZmlsZW5hbWUgPSBcIkQ6XFxcXGh1bGlhbmdcXFxcamF2YVxcXFxpZGVhd29ya3NwYWNlXFxcXGRldnRvb2xzXFxcXG15a25nXFxcXGtiLXdlYlxcXFx2aXRlLmNvbmZpZy50c1wiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9pbXBvcnRfbWV0YV91cmwgPSBcImZpbGU6Ly8vRDovaHVsaWFuZy9qYXZhL2lkZWF3b3Jrc3BhY2UvZGV2dG9vbHMvbXlrbmcva2Itd2ViL3ZpdGUuY29uZmlnLnRzXCI7aW1wb3J0IHsgZGVmaW5lQ29uZmlnLCBsb2FkRW52IH0gZnJvbSAndml0ZSdcclxuaW1wb3J0IHZ1ZSBmcm9tICdAdml0ZWpzL3BsdWdpbi12dWUnXHJcbmltcG9ydCBBdXRvSW1wb3J0IGZyb20gJ3VucGx1Z2luLWF1dG8taW1wb3J0L3ZpdGUnXHJcbmltcG9ydCBDb21wb25lbnRzIGZyb20gJ3VucGx1Z2luLXZ1ZS1jb21wb25lbnRzL3ZpdGUnXHJcbmltcG9ydCB7IEVsZW1lbnRQbHVzUmVzb2x2ZXIgfSBmcm9tICd1bnBsdWdpbi12dWUtY29tcG9uZW50cy9yZXNvbHZlcnMnXHJcbmltcG9ydCBwYXRoIGZyb20gJ3BhdGgnXHJcblxyXG5leHBvcnQgZGVmYXVsdCBkZWZpbmVDb25maWcoKHsgbW9kZSB9KSA9PiB7XHJcbiAgY29uc3QgZW52ID0gbG9hZEVudihtb2RlLCBwcm9jZXNzLmN3ZCgpLCAnJylcclxuICBjb25zdCBjdHggPSBlbnYuVklURV9DT05URVhUX1BBVEggfHwgJy9rYidcclxuXHJcbiAgcmV0dXJuIHtcclxuICAgIGJhc2U6IGAke2N0eH0vcy9gLFxyXG4gICAgcGx1Z2luczogW1xyXG4gICAgICB2dWUoKSxcclxuICAgICAgQXV0b0ltcG9ydCh7XHJcbiAgICAgICAgcmVzb2x2ZXJzOiBbRWxlbWVudFBsdXNSZXNvbHZlcigpXSxcclxuICAgICAgICBpbXBvcnRzOiBbJ3Z1ZScsICd2dWUtcm91dGVyJywgJ3BpbmlhJ10sXHJcbiAgICAgICAgZHRzOiAnc3JjL2F1dG8taW1wb3J0cy5kLnRzJyxcclxuICAgICAgfSksXHJcbiAgICAgIENvbXBvbmVudHMoe1xyXG4gICAgICAgIHJlc29sdmVyczogW0VsZW1lbnRQbHVzUmVzb2x2ZXIoeyBpbXBvcnRTdHlsZTogJ2NzcycgfSldLFxyXG4gICAgICAgIGR0czogJ3NyYy9jb21wb25lbnRzLmQudHMnLFxyXG4gICAgICB9KSxcclxuICAgIF0sXHJcbiAgICByZXNvbHZlOiB7XHJcbiAgICAgIGFsaWFzOiB7XHJcbiAgICAgICAgJ0AnOiBwYXRoLnJlc29sdmUoX19kaXJuYW1lLCAnc3JjJyksXHJcbiAgICAgIH0sXHJcbiAgICB9LFxyXG4gICAgc2VydmVyOiB7XHJcbiAgICAgIHBvcnQ6IDMwMDAsXHJcbiAgICAgIHByb3h5OiB7XHJcbiAgICAgICAgW2Ake2N0eH0vYXBpYF06IHtcclxuICAgICAgICAgIHRhcmdldDogJ2h0dHA6Ly9sb2NhbGhvc3Q6ODA4MCcsXHJcbiAgICAgICAgICBjaGFuZ2VPcmlnaW46IHRydWUsXHJcbiAgICAgICAgfSxcclxuICAgICAgfSxcclxuICAgIH0sXHJcbiAgICBidWlsZDoge1xyXG4gICAgICB0YXJnZXQ6ICdlczIwMTUnLCAgLy8gXHU3M0IwXHU0RUUzXHU2RDRGXHU4OUM4XHU1NjY4XHU3NkVFXHU2ODA3XHVGRjBDXHU4REYzXHU4RkM3XHU0RTBEXHU1RkM1XHU4OTgxXHU3Njg0IHBvbHlmaWxsXHJcbiAgICAgIGNodW5rU2l6ZVdhcm5pbmdMaW1pdDogMTYwMCwgIC8vIHdhbmdlZGl0b3IgXHU1MzU1XHU0RjUzIH4xLjZNQlx1RkYwQ1x1NjVFMFx1NkNENVx1NTE4RFx1NjJDNlxyXG4gICAgICByb2xsdXBPcHRpb25zOiB7XHJcbiAgICAgICAgb3V0cHV0OiB7XHJcbiAgICAgICAgICBtYW51YWxDaHVua3M6IHtcclxuICAgICAgICAgICAgJ3Z1ZS12ZW5kb3InOiBbJ3Z1ZScsICd2dWUtcm91dGVyJywgJ3BpbmlhJ10sXHJcbiAgICAgICAgICAgICdheGlvcyc6IFsnYXhpb3MnXSxcclxuICAgICAgICAgICAgJ3dhbmdlZGl0b3InOiBbJ0B3YW5nZWRpdG9yL2VkaXRvcicsICdAd2FuZ2VkaXRvci9lZGl0b3ItZm9yLXZ1ZSddLFxyXG4gICAgICAgICAgICAvLyBcdTRFRTVcdTRFMEJcdTUxRTBcdTRFMkFcdTVERjJcdTU3MjhcdTRFMUFcdTUyQTFcdTRFRTNcdTc4MDFcdTkxQ0NcdTY1MzlcdTYyMTAgYXdhaXQgaW1wb3J0KCkgXHU1MkE4XHU2MDAxXHU1MkEwXHU4RjdEXHJcbiAgICAgICAgICAgIC8vIFx1NEUwRFx1NTcyOCBtYW51YWxDaHVua3MgXHU5MUNDXHU1NDdEXHU1NDBEXHVGRjBDXHU5MDdGXHU1MTREXHU4OEFCIFZpdGUgXHU5OTk2XHU1QzRGIG1vZHVsZXByZWxvYWRcclxuICAgICAgICAgICAgJ3hsc3gnOiBbJ3hsc3gnXSxcclxuICAgICAgICAgICAgJ2RvbXB1cmlmeSc6IFsnZG9tcHVyaWZ5J10sXHJcbiAgICAgICAgICB9LFxyXG4gICAgICAgIH0sXHJcbiAgICAgIH0sXHJcbiAgICB9LFxyXG4gIH1cclxufSlcclxuIl0sCiAgIm1hcHBpbmdzIjogIjtBQUF5VixTQUFTLGNBQWMsZUFBZTtBQUMvWCxPQUFPLFNBQVM7QUFDaEIsT0FBTyxnQkFBZ0I7QUFDdkIsT0FBTyxnQkFBZ0I7QUFDdkIsU0FBUywyQkFBMkI7QUFDcEMsT0FBTyxVQUFVO0FBTGpCLElBQU0sbUNBQW1DO0FBT3pDLElBQU8sc0JBQVEsYUFBYSxDQUFDLEVBQUUsS0FBSyxNQUFNO0FBQ3hDLFFBQU0sTUFBTSxRQUFRLE1BQU0sUUFBUSxJQUFJLEdBQUcsRUFBRTtBQUMzQyxRQUFNLE1BQU0sSUFBSSxxQkFBcUI7QUFFckMsU0FBTztBQUFBLElBQ0wsTUFBTSxHQUFHLEdBQUc7QUFBQSxJQUNaLFNBQVM7QUFBQSxNQUNQLElBQUk7QUFBQSxNQUNKLFdBQVc7QUFBQSxRQUNULFdBQVcsQ0FBQyxvQkFBb0IsQ0FBQztBQUFBLFFBQ2pDLFNBQVMsQ0FBQyxPQUFPLGNBQWMsT0FBTztBQUFBLFFBQ3RDLEtBQUs7QUFBQSxNQUNQLENBQUM7QUFBQSxNQUNELFdBQVc7QUFBQSxRQUNULFdBQVcsQ0FBQyxvQkFBb0IsRUFBRSxhQUFhLE1BQU0sQ0FBQyxDQUFDO0FBQUEsUUFDdkQsS0FBSztBQUFBLE1BQ1AsQ0FBQztBQUFBLElBQ0g7QUFBQSxJQUNBLFNBQVM7QUFBQSxNQUNQLE9BQU87QUFBQSxRQUNMLEtBQUssS0FBSyxRQUFRLGtDQUFXLEtBQUs7QUFBQSxNQUNwQztBQUFBLElBQ0Y7QUFBQSxJQUNBLFFBQVE7QUFBQSxNQUNOLE1BQU07QUFBQSxNQUNOLE9BQU87QUFBQSxRQUNMLENBQUMsR0FBRyxHQUFHLE1BQU0sR0FBRztBQUFBLFVBQ2QsUUFBUTtBQUFBLFVBQ1IsY0FBYztBQUFBLFFBQ2hCO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxJQUNBLE9BQU87QUFBQSxNQUNMLFFBQVE7QUFBQTtBQUFBLE1BQ1IsdUJBQXVCO0FBQUE7QUFBQSxNQUN2QixlQUFlO0FBQUEsUUFDYixRQUFRO0FBQUEsVUFDTixjQUFjO0FBQUEsWUFDWixjQUFjLENBQUMsT0FBTyxjQUFjLE9BQU87QUFBQSxZQUMzQyxTQUFTLENBQUMsT0FBTztBQUFBLFlBQ2pCLGNBQWMsQ0FBQyxzQkFBc0IsNEJBQTRCO0FBQUE7QUFBQTtBQUFBLFlBR2pFLFFBQVEsQ0FBQyxNQUFNO0FBQUEsWUFDZixhQUFhLENBQUMsV0FBVztBQUFBLFVBQzNCO0FBQUEsUUFDRjtBQUFBLE1BQ0Y7QUFBQSxJQUNGO0FBQUEsRUFDRjtBQUNGLENBQUM7IiwKICAibmFtZXMiOiBbXQp9Cg==
