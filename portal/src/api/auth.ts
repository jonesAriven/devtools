import { authRequest } from './request'

export interface LoginRequest {
  username: string
  password: string
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
