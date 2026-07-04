import request from '@/utils/request'
import type { R, PageResult, OpsKnowledge, OpsKnowledgeRequest, PageParams } from '@/types'

export function getKnowledgeList(params: PageParams & { keyword?: string; category?: string }) {
  return request.get<R<PageResult<OpsKnowledge>>>('/ops/knowledge/list', { params })
}

export function getKnowledge(id: number) {
  return request.get<R<OpsKnowledge>>(`/ops/knowledge/${id}`)
}

export function createKnowledge(data: OpsKnowledgeRequest) {
  return request.post<R<OpsKnowledge>>('/ops/knowledge', data)
}

export function updateKnowledge(id: number, data: OpsKnowledgeRequest) {
  return request.put<R<OpsKnowledge>>(`/ops/knowledge/${id}`, data)
}

export function deleteKnowledge(id: number) {
  return request.delete<R<void>>(`/ops/knowledge/${id}`)
}
