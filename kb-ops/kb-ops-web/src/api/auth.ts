import { authRequest } from '@/utils/request'
import type { LoginRequest, LoginResponse, User } from '@/types'

export function login(data: LoginRequest): Promise<LoginResponse> {
  return authRequest.post('/login', data) as Promise<LoginResponse>
}

export function logout(): Promise<void> {
  return authRequest.post('/logout') as Promise<void>
}

export function refreshToken(refreshToken: string): Promise<{ accessToken: string; refreshToken: string }> {
  return authRequest.post('/refresh', { refreshToken }) as Promise<{ accessToken: string; refreshToken: string }>
}

export function getUserProfile(): Promise<LoginResponse> {
  return authRequest.get('/me') as Promise<LoginResponse>
}
