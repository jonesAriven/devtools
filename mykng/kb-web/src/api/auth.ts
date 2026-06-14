import request from './index'
import type { R, LoginRequest, LoginResponse, RefreshTokenRequest, RefreshTokenResponse } from '@/types'

/** 用户登录 */
export function login(data: LoginRequest) {
  return request.post<R<LoginResponse>>('/auth/login', data)
}

/** 刷新Token */
export function refreshToken(data: RefreshTokenRequest) {
  return request.post<R<RefreshTokenResponse>>('/auth/refresh', data)
}

/** 用户登出 */
export function logout() {
  return request.post<R<void>>('/auth/logout')
}

/** 获取当前用户信息 */
export function getCurrentUser() {
  return request.get<R<LoginResponse>>('/auth/me')
}
