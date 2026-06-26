import request from './request'
import type { Space, Folder, Doc, Tag, Share, Version, TrashItem, PageResult } from '@/types/api'

// 空间
export const spaceApi = {
  list: () => request.get<Space[]>('/space/list'),
  create: (data: { name: string; type: string; description: string }) => request.post<Space>('/space', data),
  update: (id: number, data: { name: string; description: string }) => request.put<Space>(`/space/${id}`, data),
  delete: (id: number) => request.delete(`/space/${id}`),
}

// 目录
export const folderApi = {
  tree: (spaceId: number) => request.get<Folder[]>(`/folder/tree/${spaceId}`),
  create: (data: { spaceId: number; parentId: number; name: string }) => request.post<Folder>('/folder', data),
  delete: (id: number) => request.delete(`/folder/${id}`),
}

// 文档
export const docApi = {
  list: (params: { folderId?: number; page: number; size: number }) =>
    request.get<PageResult<Doc>>('/doc/list', { params }),
  get: (id: number) => request.get<Doc>(`/doc/${id}`),
  create: (data: { folderId: number; title: string; content: string }) => request.post<Doc>('/doc', data),
  update: (id: number, data: { title: string; content: string }) => request.put<Doc>(`/doc/${id}`, data),
  delete: (id: number) => request.delete(`/doc/${id}`),
  star: (id: number) => request.put(`/doc/${id}/star`),
}

// 搜索
export const searchApi = {
  search: (params: { q: string; page: number; size: number }) =>
    request.get<PageResult<Doc>>('/search', { params }),
}

// 标签
export const tagApi = {
  list: () => request.get<Tag[]>('/tag/list'),
  create: (data: { name: string; color: string }) => request.post<Tag>('/tag', data),
  delete: (id: number) => request.delete(`/tag/${id}`),
  bind: (data: { tagId: number; resourceType: string; resourceId: number }) => request.post('/tag/bind', data),
}

// 分享
export const shareApi = {
  list: () => request.get<Share[]>('/share/list'),
  create: (data: { resourceType: string; resourceId: number; extractCode?: string; expireDays?: number }) =>
    request.post<Share>('/share', data),
  delete: (id: number) => request.delete(`/share/${id}`),
  verify: (code: string, extractCode?: string) =>
    request.get(`/share/verify/${code}`, { params: { extractCode } }),
}

// 版本
export const versionApi = {
  list: (type: string, id: number) => request.get<Version[]>(`/version/list/${type}/${id}`),
}

// 回收站
export const trashApi = {
  list: (params: { page: number; size: number }) =>
    request.get<PageResult<TrashItem>>('/trash/list', { params }),
  restore: (type: string, id: number) => request.post(`/trash/restore/${type}/${id}`),
  delete: (type: string, id: number) => request.delete(`/trash/${type}/${id}`),
}
