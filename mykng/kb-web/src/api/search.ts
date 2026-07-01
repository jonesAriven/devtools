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

/** 全文搜索 */
export function search(params: SearchParams) {
  const queryParams: Record<string, any> = {}
  if (params.keyword) queryParams.q = params.keyword
  if (params.type) queryParams.type = params.type
  if (params.folderId !== undefined && params.folderId !== null) queryParams.folderId = params.folderId
  if (params.tagId !== undefined && params.tagId !== null) queryParams.tagId = params.tagId
  if (params.page) queryParams.page = params.page
  if (params.size) queryParams.size = params.size
  return request.get<R<PageResult<SearchResult>>>('/search', { params: queryParams })
}

/** 搜索建议 */
export function searchSuggest(keyword: string) {
  return request.get<R<string[]>>('/search/suggest', { params: { q: keyword } })
}
