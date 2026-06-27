import request from './index'
import type { R, KbFile, PageResult, PageParams } from '@/types'

/** 简单上传文件（小文件），返回文件ID字符串 */
export function uploadFile(formData: FormData, onProgress?: (progress: number) => void) {
  return request.post<R<string>>('/file/upload', formData, {
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

/** 获取文件下载链接 */
export function getFileDownloadUrl(id: number) {
  return request.get<R<string>>(`/file/${id}/download`)
}

/** 下载文件（通过下载URL直接打开） */
export async function downloadFile(id: number, fileName?: string) {
  const res = await getFileDownloadUrl(id)
  const url = res.data.data
  if (url) {
    const link = document.createElement('a')
    link.href = url
    if (fileName) link.download = fileName
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }
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
