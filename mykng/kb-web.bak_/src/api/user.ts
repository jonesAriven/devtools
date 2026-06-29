import request from './index'
import type { R, User } from '@/types'

/** 获取当前用户信息 */
export function getUserProfile() {
  return request.get<R<User>>('/user/profile')
}

/** 更新用户信息 */
export function updateUserProfile(data: Partial<Pick<User, 'nickname' | 'email' | 'avatar'>>) {
  return request.put<R<User>>('/user/profile', data)
}

/** 修改密码 */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put<R<void>>('/user/password', data)
}

/** 获取用户列表 */
export function getUserList() {
  return request.get<R<User[]>>('/user/list')
}
