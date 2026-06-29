import request from './index'
import type { R, Bucket, CreateBucketRequest, UpdateBucketRequest } from '@/types'

/** 获取存储桶列表 */
export function getBucketList() {
  return request.get<R<Bucket[]>>('/bucket/list')
}

/** 获取存储桶详情 */
export function getBucketDetail(id: number) {
  return request.get<R<Bucket>>(`/bucket/${id}`)
}

/** 创建存储桶 */
export function createBucket(data: CreateBucketRequest) {
  return request.post<R<Bucket>>('/bucket', data)
}

/** 更新存储桶 */
export function updateBucket(id: number, data: UpdateBucketRequest) {
  return request.put<R<Bucket>>(`/bucket/${id}`, data)
}

/** 删除存储桶 */
export function deleteBucket(id: number) {
  return request.delete<R<void>>(`/bucket/${id}`)
}

/** 测试存储桶连接 */
export function testBucketConnection(id: number) {
  return request.post<R<boolean>>(`/bucket/${id}/test`)
}

/** 设置默认存储桶 */
export function setDefaultBucket(id: number) {
  return request.put<R<void>>(`/bucket/${id}/default`)
}
