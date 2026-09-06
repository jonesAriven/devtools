import { authRequest, adminRequest } from './request'

export interface LoginRequest {
  username: string
  password: string
}

// ---------- 统一认证（auth-center）SSO ----------

export function ssoExchangeApi(code: string, state: string): Promise<any> {
  return authRequest.post('/sso/exchange', { code, state })
}

// ---------- 用户管理（auth-center 代理，仅 admin）----------

export interface CenterUser {
  id: number
  username: string
  nickname?: string
  email?: string
  status: number
  realmId?: string
  role: string
  createdAt?: string
}

export function listCenterUsers(realmId?: string): Promise<CenterUser[]> {
  return adminRequest.get('/users', { params: realmId ? { realmId } : {} })
}

export function createCenterUser(data: { username: string; password: string; role?: string; nickname?: string; email?: string; realmId?: string }): Promise<CenterUser> {
  return adminRequest.post('/users', data)
}

export function updateCenterUser(id: number, data: { role?: string; status?: number; nickname?: string; email?: string }): Promise<CenterUser> {
  return adminRequest.put(`/users/${id}`, data)
}

export function deleteCenterUser(id: number): Promise<void> {
  return adminRequest.delete(`/users/${id}`)
}

export function resetCenterUserPassword(id: number, newPassword: string): Promise<void> {
  return adminRequest.put(`/users/${id}/password`, { newPassword })
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface LoginResponse {
  accessToken?: string
  token?: string
  username?: string
  [key: string]: any
}

export function login(data: LoginRequest): Promise<LoginResponse> {
  return authRequest.post('/login', data)
}

export function logout(): Promise<void> {
  return authRequest.post('/logout')
}

export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return authRequest.post('/change-password', data)
}

export function getUserInfo(): Promise<any> {
  return authRequest.get('/userinfo')
}
