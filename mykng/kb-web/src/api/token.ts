import request from './index'
import type { R, ApiToken, CreateTokenRequest } from '@/types'

/** 获取 Token 列表 */
export function getTokenList() {
  return request.get<R<ApiToken[]>>('/token/list')
}

/** 创建 Token */
export function createToken(data: CreateTokenRequest) {
  return request.post<R<ApiToken>>('/token', data)
}

/** 删除 Token */
export function deleteToken(id: number) {
  return request.delete<R<void>>(`/token/${id}`)
}

/** 启用/禁用 Token */
export function toggleTokenStatus(id: number) {
  return request.put<R<void>>(`/token/${id}/toggle`)
}

/** 获取 Token 详情 */
export function getTokenDetail(id: number) {
  return request.get<R<ApiToken>>(`/token/${id}`)
}
