import request from './index'
import type { R, OperationLog, PageResult, PageParams } from '@/types'

/**
 * 操作日志 API
 * 操作日志已从 kb-ops 迁移至 kb-auth，由认证服务统一管理用户行为审计
 */

/** 获取操作日志列表 */
export function getLogList(params: PageParams & { action?: string; resourceType?: string; startTime?: string; endTime?: string }) {
  return request.get<R<PageResult<OperationLog>>>('/auth/log/list', { params })
}

/** 获取操作日志详情 */
export function getLogDetail(id: number) {
  return request.get<R<OperationLog>>(`/auth/log/${id}`)
}
