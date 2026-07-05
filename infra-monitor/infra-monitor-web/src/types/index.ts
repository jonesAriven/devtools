export interface R<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface PageParams {
  page: number
  size: number
  keyword?: string
  category?: string
}

export interface InfraItem {
  id: string
  type: string
  name: string
  category: string
  description: string
  extra: Record<string, any>
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface InfraItemRequest {
  type: string
  name: string
  category?: string
  description?: string
  extra?: Record<string, any>
  sortOrder?: number
}

export interface Credential {
  id: string
  name: string
  category: string
  description: string
  extra: {
    username?: string
    password?: string
    secretKey?: string
    type?: string
    host?: string
    serviceName?: string
    url?: string
    remark?: string
    [key: string]: any
  }
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface CredentialRequest {
  name: string
  category?: string
  description?: string
  extra?: Record<string, any>
  sortOrder?: number
}

export interface ConfigItem {
  id: string
  name: string
  category: string
  description: string
  extra: {
    configType?: string
    content?: any
    [key: string]: any
  }
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface ConfigItemRequest {
  name: string
  category?: string
  configType?: string
  description?: string
  content?: string
  sortOrder?: number
}

export interface ServiceItem {
  id: string
  name: string
  category: string
  description: string
  extra: {
    url?: string
    host?: string
    port?: number
    protocol?: string
    checkType?: string
    timeout?: number
    healthCheckUrl?: string
    status?: string
    latencyMs?: number
    lastCheckTime?: string
    lastError?: string
    enabled?: number
    intervalSeconds?: number
    script?: string
    hostId?: string
    credentialId?: string
    techStack?: string
    [key: string]: any
  }
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface HealthCheckResult {
  id: string
  name: string
  status: string
  latencyMs?: number
  errorMsg?: string
  checkUrl?: string
}

export interface HealthCheckAllResult {
  total: number
  online: number
  offline: number
  unknown: number
  results: HealthCheckResult[]
}

export interface DashboardSummary {
  hostCount: number
  credentialCount: number
  configCount: number
  serviceCount: number
  servicesOnline: number
  servicesOffline: number
  servicesUnknown: number
}

export type CredentialType = 'WEB' | 'DB' | 'SSH' | 'API_TOKEN' | 'OTHER'
export type ConfigCategory = 'NETWORK' | 'STORAGE' | 'CACHE' | 'CERT' | 'DEPLOY' | 'PROXY' | 'OTHER'
export type ConfigType = 'TEXT' | 'JSON' | 'KEY_VALUE' | 'LIST' | 'TABLE'
