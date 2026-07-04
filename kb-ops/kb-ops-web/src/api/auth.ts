import request from '@/utils/request'
import type { R, LoginRequest, LoginResponse, User } from '@/types'

export function login(data: LoginRequest) {
  return request.post<R<LoginResponse>>('/ops/auth/login', data)
}

export function logout() {
  return request.post<R<void>>('/ops/auth/logout')
}

export function refreshToken(refreshToken: string) {
  return request.post<R<{ accessToken: string; refreshToken: string }>>('/ops/auth/refresh', { refreshToken })
}

export function getUserProfile() {
  return request.get<R<User>>('/ops/auth/profile')
}
