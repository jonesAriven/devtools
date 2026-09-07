// axios 模块增补（必须放在有 import/export 的模块文件里才是 augmentation；
// 若写在 ambient 声明文件（无 import/export 的 .d.ts）里会整体替换 axios 的类型声明）
import 'axios'

declare module 'axios' {
  export interface AxiosRequestConfig<D = any> {
    /** 401 静默续期时标记请求已重试过，防止无限循环 */
    _retry?: boolean
  }
}
