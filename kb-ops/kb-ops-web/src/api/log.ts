import request from '@/utils/request'
import type { R, PageResult, OperationLog, PageParams } from '@/types'

export function getLogList(params: PageParams & { keyword?: string; action?: string; resourceType?: string }) {
  return request.get<R<PageResult<OperationLog>>>('/ops/log/list', { params })
}

export function getLog(id: number) {
  return request.get<R<OperationLog>>(`/ops/log/${id}`)
}
