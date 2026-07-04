import request from './index'
import type { R, PageResult, PageParams } from '@/types'

export interface RequestLog {
  id: number
  traceId?: string
  userId?: number
  username?: string
  httpMethod?: string
  requestUri?: string
  controllerMethod?: string
  requestArgs?: string
  responseResult?: string
  costMs?: number
  status?: string
  exception?: string
  ip?: string
  userAgent?: string
  serviceName?: string
  createdAt: string
}

export function getRequestLogList(params: PageParams & {
  traceId?: string
  userId?: number
  httpMethod?: string
  uri?: string
  status?: string
  serviceName?: string
  startTime?: string
  endTime?: string
}) {
  return request.get<R<PageResult<RequestLog>>>('/auth/request-log/list', { params })
}

export function getRequestLogDetail(id: number) {
  return request.get<R<RequestLog>>(`/auth/request-log/${id}`)
}
