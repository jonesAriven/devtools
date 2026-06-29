import request from './index'
import type { R, ApiToken, CreateTokenRequest, PageResult } from '@/types'

/** 获取 Token 列表 */
export function getTokenList(params?: { page?: number; size?: number }) {
  return request.get<R<PageResult<ApiToken>>>('/token', { params })
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
