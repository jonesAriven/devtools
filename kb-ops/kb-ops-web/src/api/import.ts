import request from '@/utils/request'
import type { R, ImportResult } from '@/types'

export function importData(type: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<R<ImportResult>>(`/ops/import/${type}`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function getImportTemplate(type: string) {
  return request.get(`/ops/import/${type}/template`, {
    responseType: 'blob',
  })
}
