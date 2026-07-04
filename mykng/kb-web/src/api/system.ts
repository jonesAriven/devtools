import request from './index'
import type { R, ModuleStatus } from '@/types'

/** 获取所有已注册模块的状态 */
export function getModuleStatus() {
  return request.get<R<ModuleStatus[]>>('/system/modules')
}
