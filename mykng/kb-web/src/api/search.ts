import request from './index'
import type { R, SearchResult, PageResult } from '@/types'

interface SearchParams {
  keyword?: string
  type?: string
  folderId?: number
  tagId?: number
  page?: number
  size?: number
}

/** 全文搜索（支持 AbortSignal 取消请求） */
export function search(params: SearchParams, signal?: AbortSignal) {
  const queryParams: Record<string, any> = {}
  if (params.keyword) queryParams.q = params.keyword
  if (params.type) queryParams.type = params.type
  if (params.folderId !== undefined && params.folderId !== null) queryParams.folderId = params.folderId
  if (params.tagId !== undefined && params.tagId !== null) queryParams.tagId = params.tagId
  if (params.page) queryParams.page = params.page
  if (params.size) queryParams.size = params.size
  return request.get<R<PageResult<SearchResult>>>('/search', {
    params: queryParams,
    signal,
  })
}

/** 搜索建议 */
export function searchSuggest(keyword: string) {
  return request.get<R<string[]>>('/search/suggest', { params: { q: keyword } })
}

/** 收藏列表（服务端分页） */
export function getStarredList(params: { type?: string; page?: number; size?: number }) {
  const queryParams: Record<string, any> = {}
  if (params.type && params.type !== 'all') queryParams.type = params.type
  if (params.page) queryParams.page = params.page
  if (params.size) queryParams.size = params.size
  return request.get<R<PageResult<SearchResult>>>('/search/starred', { params: queryParams })
}
