import request from './index'
import type { R, PageResult, PageParams } from '@/types'

export interface ErrorLog {
  id: number
  userId?: number
  username?: string
  level: string
  source: string
  message: string
  stackTrace?: string
  url?: string
  ip?: string
  createdAt: string
}

export function reportError(data: {
  level: string
  source: string
  message: string
  stackTrace?: string
  url?: string
}) {
  return request.post<R<void>>('/auth/error-log/report', data)
}

export function getErrorLogList(params: PageParams & {
  level?: string
  source?: string
  startTime?: string
  endTime?: string
}) {
  return request.get<R<PageResult<ErrorLog>>>('/auth/error-log/list', { params })
}

export function getErrorLogDetail(id: number) {
  return request.get<R<ErrorLog>>(`/auth/error-log/${id}`)
}
