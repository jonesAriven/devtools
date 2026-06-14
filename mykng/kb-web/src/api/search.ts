import request from './index'
import type { R, SearchResult, SearchParams, PageResult } from '@/types'

/** 全文搜索 */
export function search(params: SearchParams) {
  return request.get<R<PageResult<SearchResult>>>('/search', { params })
}

/** 搜索建议 */
export function searchSuggest(keyword: string) {
  return request.get<R<string[]>>('/search/suggest', { params: { keyword } })
}
