import request from './request'
import type { SystemConfig, SystemCategory, SystemCredentials } from '@/config/systems'

export interface SystemQueryParams {
  page?: number
  pageSize?: number
  category?: SystemCategory
  keyword?: string
  status?: number
  hasCredentials?: boolean
  hasUrl?: boolean
}

export interface SystemPageResult {
  list: SystemConfig[]
  total: number
  page: number
  size: number
}

interface RawSystem {
  id: number
  name: string
  description: string
  url?: string
  urlPublic?: string
  urlLan?: string
  urlTailscale?: string
  icon: string
  color: string
  category: string
  status: number
  healthCheckUrl?: string
  docs?: string
  downloadPath?: string
  techStack?: string
  loginUsername?: string
  loginPassword?: string
  sortOrder: number
}

function transformSystem(raw: RawSystem): SystemConfig {
  let docs: { label: string; url: string }[] | undefined
  if (raw.docs) {
    try {
      docs = JSON.parse(raw.docs)
    } catch {
      docs = undefined
    }
  }
  return {
    id: String(raw.id),
    name: raw.name,
    description: raw.description || '',
    url: raw.url,
    urlPublic: raw.urlPublic,
    urlLan: raw.urlLan,
    urlTailscale: raw.urlTailscale,
    icon: raw.icon,
    color: raw.color,
    category: (raw.category as SystemCategory) || 'web',
    healthCheckUrl: raw.healthCheckUrl,
    docs,
    downloadPath: raw.downloadPath,
    techStack: raw.techStack,
    loginUsername: raw.loginUsername,
    loginPassword: raw.loginPassword,
  }
}

export function getSystemList(params?: SystemQueryParams): Promise<SystemPageResult> {
  const queryParams = {
    page: params?.page,
    size: params?.pageSize,
    category: params?.category,
    keyword: params?.keyword,
    status: params?.status,
    hasCredentials: params?.hasCredentials,
    hasUrl: params?.hasUrl,
  }
  return request.get<any, any>('/system/list', { params: queryParams }).then((res: any) => ({
    list: (res.records || res.list || []).map(transformSystem),
    total: res.total || 0,
    page: res.current || res.page || 1,
    size: res.size || res.pageSize || 20,
  }))
}

export function getAllSystems(): Promise<SystemConfig[]> {
  return request.get<any, any[]>('/system/all').then((list: any[]) => list.map(transformSystem))
}

export function getSystemById(id: string): Promise<SystemConfig> {
  return request.get<any, any>(`/system/${id}`).then(transformSystem)
}

export function getSystemCredentials(id: string): Promise<SystemCredentials> {
  return request.get<any, any>(`/system/${id}/credentials`).then((res: any) => ({
    username: res.username || '',
    password: res.password || '',
  }))
}

export function createSystem(data: Partial<SystemConfig>): Promise<SystemConfig> {
  const payload = {
    name: data.name,
    description: data.description,
    url: data.url,
    icon: data.icon,
    color: data.color,
    category: data.category,
    status: 1,
    healthCheckUrl: data.healthCheckUrl,
    docs: data.docs ? JSON.stringify(data.docs) : undefined,
    downloadPath: data.downloadPath,
    techStack: data.techStack,
    loginUsername: data.loginUsername,
    loginPassword: data.loginPassword,
    sortOrder: 0,
  }
  return request.post<any, any>('/system', payload).then(transformSystem)
}

export function updateSystem(id: string, data: Partial<SystemConfig>): Promise<SystemConfig> {
  const payload = {
    name: data.name,
    description: data.description,
    url: data.url,
    icon: data.icon,
    color: data.color,
    category: data.category,
    status: data.status !== undefined ? Number(data.status) : undefined,
    healthCheckUrl: data.healthCheckUrl,
    docs: data.docs ? JSON.stringify(data.docs) : undefined,
    downloadPath: data.downloadPath,
    techStack: data.techStack,
    loginUsername: data.loginUsername,
    loginPassword: data.loginPassword,
    sortOrder: data.sortOrder !== undefined ? Number(data.sortOrder) : undefined,
  }
  return request.put<any, any>(`/system/${id}`, payload).then(transformSystem)
}

export function deleteSystem(id: string): Promise<void> {
  return request.delete<any, void>(`/system/${id}`)
}
