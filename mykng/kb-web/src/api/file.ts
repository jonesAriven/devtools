import request from './index'
import type { R, KbFile, PageResult, PageParams } from '@/types'

/** 上传文件 */
export function uploadFile(formData: FormData, onProgress?: (progress: number) => void) {
  return request.post<R<KbFile>>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress(event) {
      if (event.total && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    },
  })
}

/** 分片上传初始化 */
export function initChunkUpload(data: {
  name: string
  fileSize: number
  folderId: number
  spaceId: number
  mimeType: string
  totalChunks: number
}) {
  return request.post<R<{ uploadId: string; storageKey: string }>>('/file/chunk/init', data)
}

/** 分片上传 */
export function uploadChunk(formData: FormData, onProgress?: (progress: number) => void) {
  return request.post<R<void>>('/file/chunk/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress(event) {
      if (event.total && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    },
  })
}

/** 分片上传完成 */
export function completeChunkUpload(data: { uploadId: string; storageKey: string }) {
  return request.post<R<KbFile>>('/file/chunk/complete', data)
}

/** 获取文件详情 */
export function getFileDetail(id: number) {
  return request.get<R<KbFile>>(`/file/${id}`)
}

/** 获取目录下的文件列表 */
export function getFileList(params: PageParams & { folderId: number }) {
  return request.get<R<PageResult<KbFile>>>('/file/list', { params })
}

/** 删除文件 */
export function deleteFile(id: number) {
  return request.delete<R<void>>(`/file/${id}`)
}

/** 下载文件 */
export function downloadFile(id: number) {
  return request.get<Blob>(`/file/${id}/download`, { responseType: 'blob' })
}

/** 获取文件内容（文本类文件预览） */
export function getFileContent(id: number) {
  return request.get<R<string>>(`/file/${id}/content`)
}

/** 切换文件星标 */
export function toggleFileStar(id: number) {
  return request.put<R<void>>(`/file/${id}/star`)
}

/** 移动文件 */
export function moveFile(id: number, folderId: number) {
  return request.put<R<void>>(`/file/${id}/move`, { folderId })
}
