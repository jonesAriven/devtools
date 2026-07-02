import { ElMessageBox } from 'element-plus'

/**
 * 统一的删除/危险操作确认弹窗。
 *
 * - 确认按钮文案统一为「确定删除」，避免默认「确定」导致用户在危险操作上不够警觉
 * - 用户取消会 reject，调用方需 try/catch 或 .catch 处理（与原 ElMessageBox.confirm 行为一致）
 *
 * @example
 * try {
 *   await confirmDelete('确定要删除该笔记吗？')
 *   // 执行删除
 * } catch {
 *   // 用户取消
 * }
 */
export function confirmDelete(
  message: string,
  options: {
    title?: string
    confirmButtonText?: string
    cancelButtonText?: string
  } = {},
) {
  return ElMessageBox.confirm(message, options.title || '警告', {
    type: 'warning',
    confirmButtonText: options.confirmButtonText || '确定删除',
    cancelButtonText: options.cancelButtonText || '取消',
  })
}

/**
 * 统一的清空/批量操作确认弹窗。
 */
export function confirmDanger(
  message: string,
  confirmButtonText = '确定',
  title = '警告',
) {
  return ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText,
    cancelButtonText: '取消',
  })
}
