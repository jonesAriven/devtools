import request from './request'
import type { User, LoginResult } from '@/types/api'

export function login(data: { username: string; password: string }) {
  return request.post<LoginResult>('/auth/login', data)
}

export function refresh(data: { refreshToken: string }) {
  return request.post<LoginResult>('/auth/refresh', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getProfile() {
  return request.get<User>('/user/profile')
}
