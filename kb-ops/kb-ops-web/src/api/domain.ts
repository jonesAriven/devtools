import request from '@/utils/request'
import type { R, PageResult, Domain, DomainRequest, PageParams } from '@/types'

export function getDomainList(params: PageParams & { keyword?: string }) {
  return request.get<R<PageResult<Domain>>>('/ops/domain/list', { params })
}

export function getDomain(id: number) {
  return request.get<R<Domain>>(`/ops/domain/${id}`)
}

export function createDomain(data: DomainRequest) {
  return request.post<R<Domain>>('/ops/domain', data)
}

export function updateDomain(id: number, data: DomainRequest) {
  return request.put<R<Domain>>(`/ops/domain/${id}`, data)
}

export function deleteDomain(id: number) {
  return request.delete<R<void>>(`/ops/domain/${id}`)
}
