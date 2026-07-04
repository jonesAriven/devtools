import request from './request'
import type { SystemConfig, SystemCategory } from '@/config/systems'

export interface SystemQueryParams {
  page?: number
  pageSize?: number
  category?: SystemCategory
  keyword?: string
}

export interface SystemPageResult {
  list: SystemConfig[]
  total: number
  page: number
  pageSize: number
}

export function getSystemList(params?: SystemQueryParams): Promise<SystemPageResult> {
  return request.get('/portal/system/list', { params })
}

export function getAllSystems(): Promise<SystemConfig[]> {
  return request.get('/portal/system/all')
}

export function getSystemById(id: string): Promise<SystemConfig> {
  return request.get(`/portal/system/${id}`)
}

export function createSystem(data: Partial<SystemConfig>): Promise<SystemConfig> {
  return request.post('/portal/system', data)
}

export function updateSystem(id: string, data: Partial<SystemConfig>): Promise<SystemConfig> {
  return request.put(`/portal/system/${id}`, data)
}

export function deleteSystem(id: string): Promise<void> {
  return request.delete(`/portal/system/${id}`)
}
