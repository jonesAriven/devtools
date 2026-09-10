import request from './index'
import type { ModuleStatus } from '@/types'

/**
 * 获取所有已注册模块的状态。
 *
 * ⚠️ 该端点由网关**自身**提供（不转发给后端服务），返回的是**裸数组**，
 * 没有其它接口的 {code,message,data} 信封 —— 网关未依赖 common-core，也不打算为此引入依赖。
 * 因此这里按 ModuleStatus[] 声明；拦截器对无 code 字段的数组响应体不再做业务码校验。
 */
export function getModuleStatus() {
  return request.get<ModuleStatus[]>('/system/modules')
}
