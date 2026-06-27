import request from './index'
import type { R, SearchResult, PageResult } from '@/types'

interface SearchParams {
  keyword?: string
  type?: string
  folderId?: number
  tagId?: number
  page?: number
  pageSize?: number
}

/** 全文搜索 */
export function search(params: SearchParams) {
  return request.get<R<PageResult<SearchResult>>>('/search', {
    params: {
      q: params.keyword,
      type: params.type,
      folderId: params.folderId,
      tagId: params.tagId,
      page: params.page,
      size: params.pageSize,
    },
  })
}

/** 搜索建议 */
export function searchSuggest(keyword: string) {
  return request.get<R<string[]>>('/search/suggest', { params: { q: keyword } })
}
