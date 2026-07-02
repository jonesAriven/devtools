import request from './index'
import type { R, OperationLog, PageResult, PageParams } from '@/types'

/**
 * 操作日志 API
 * 微服务版：操作日志通过 Redis Pub/Sub 事件收集，由 kb-ops 聚合查询
 */

/** 获取操作日志列表 */
export function getLogList(params: PageParams & { action?: string; resourceType?: string; startTime?: string; endTime?: string }) {
  return request.get<R<PageResult<OperationLog>>>('/ops/log/list', { params })
}

/** 获取操作日志详情 */
export function getLogDetail(id: number) {
  return request.get<R<OperationLog>>(`/ops/log/${id}`)
}
