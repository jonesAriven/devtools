import request from '@/utils/request'
import type { R, PageResult, Credential, CredentialRequest, PageParams } from '@/types'

export function getCredentialList(params: PageParams & { keyword?: string; hostId?: number }) {
  return request.get<R<PageResult<Credential>>>('/ops/credential/list', { params })
}

export function getCredential(id: number) {
  return request.get<R<Credential>>(`/ops/credential/${id}`)
}

export function createCredential(data: CredentialRequest) {
  return request.post<R<Credential>>('/ops/credential', data)
}

export function updateCredential(id: number, data: CredentialRequest) {
  return request.put<R<Credential>>(`/ops/credential/${id}`, data)
}

export function deleteCredential(id: number) {
  return request.delete<R<void>>(`/ops/credential/${id}`)
}
