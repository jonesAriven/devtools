import request from './index'
import type { R, OperationLog, PageResult, PageParams } from '@/types'

/** 获取操作日志列表 */
export function getLogList(params: PageParams & { action?: string; resourceType?: string }) {
  return request.get<R<PageResult<OperationLog>>>('/log/list', { params })
}

/** 获取操作日志详情 */
export function getLogDetail(id: number) {
  return request.get<R<OperationLog>>(`/log/${id}`)
}
