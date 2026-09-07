/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
// 注意：本文件是无 import 的 ambient 声明文件——在 ambient 文件里 declare module 'pkg'
// 会「整体替换」该包的类型声明（不是增补），axios 类型曾被这样遮蔽导致全站 TS 报错。
// axios 的 _retry 增补已移至 src/types/axios-augment.d.ts（带 import，是真正的模块增补）。
