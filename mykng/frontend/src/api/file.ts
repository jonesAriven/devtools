import request from './request'
import type { Bucket, KbFile, PageResult } from '@/types/api'

export const bucketApi = {
  list: () => request.get<Bucket[]>('/bucket/list'),
}

export const fileApi = {
  list: (params: { bucketId?: number; page: number; size: number }) =>
    request.get<PageResult<KbFile>>('/file/list', { params }),
}
