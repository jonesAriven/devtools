import request from '@/utils/request'
import type { R, PageResult, Host, HostRequest, PageParams } from '@/types'

export function getHostList(params: PageParams & { keyword?: string }) {
  return request.get<R<PageResult<Host>>>('/ops/host/list', { params })
}

export function getHost(id: number) {
  return request.get<R<Host>>(`/ops/host/${id}`)
}

export function createHost(data: HostRequest) {
  return request.post<R<Host>>('/ops/host', data)
}

export function updateHost(id: number, data: HostRequest) {
  return request.put<R<Host>>(`/ops/host/${id}`, data)
}

export function deleteHost(id: number) {
  return request.delete<R<void>>(`/ops/host/${id}`)
}

export function getHostAll() {
  return request.get<R<Host[]>>('/ops/host/all')
}
