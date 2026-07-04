import request from '@/utils/request'
import type { R, PageResult, Port, PortRequest, PageParams } from '@/types'

export function getPortList(params: PageParams & { keyword?: string; hostId?: number }) {
  return request.get<R<PageResult<Port>>>('/ops/port/list', { params })
}

export function getPort(id: number) {
  return request.get<R<Port>>(`/ops/port/${id}`)
}

export function createPort(data: PortRequest) {
  return request.post<R<Port>>('/ops/port', data)
}

export function updatePort(id: number, data: PortRequest) {
  return request.put<R<Port>>(`/ops/port/${id}`, data)
}

export function deletePort(id: number) {
  return request.delete<R<void>>(`/ops/port/${id}`)
}
