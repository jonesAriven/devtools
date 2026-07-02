/**
 * 格式化文件大小
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const size = bytes / Math.pow(k, i)
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

/**
 * 格式化日期为 YYYY-MM-DD HH:mm
 */
export function formatDate(date: string | Date | undefined | null): string {
  if (!date) return ''
  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) return ''
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

/**
 * 格式化为相对时间（如"3分钟前"）
 */
export function formatRelativeTime(date: string | Date | undefined | null): string {
  if (!date) return ''
  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 0) return '刚刚'
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  const months = Math.floor(days / 30)
  const years = Math.floor(months / 12)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 30) return `${days}天前`
  if (months < 12) return `${months}个月前`
  return `${years}年前`
}

/**
 * 截断文本
 */
export function truncateText(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

/**
 * 获取文件扩展名
 */
export function getFileExtension(filename: string): string {
  const lastDot = filename.lastIndexOf('.')
  if (lastDot === -1) return ''
  return filename.slice(lastDot + 1).toLowerCase()
}

/**
 * 根据文件类型获取图标名称
 */
export function getFileIcon(mimeType: string): string {
  if (mimeType.startsWith('image/')) return 'Picture'
  if (mimeType.startsWith('video/')) return 'VideoCamera'
  if (mimeType.startsWith('audio/')) return 'Headset'
  if (mimeType.includes('pdf')) return 'Document'
  if (mimeType.includes('word') || mimeType.includes('document')) return 'Document'
  if (mimeType.includes('excel') || mimeType.includes('spreadsheet')) return 'Grid'
  if (mimeType.includes('powerpoint') || mimeType.includes('presentation')) return 'DataBoard'
  if (mimeType.startsWith('text/')) return 'Document'
  if (mimeType.includes('zip') || mimeType.includes('rar') || mimeType.includes('7z') || mimeType.includes('tar')) return 'Folder'
  return 'Document'
}

/**
 * 资源类型中文标签映射
 */
const RESOURCE_TYPE_LABELS: Record<string, string> = {
  folder: '目录',
  file: '文件',
  doc: '笔记',
  web: '网页',
}

/**
 * 获取资源类型的中文标签
 */
export function typeLabel(type: string): string {
  return RESOURCE_TYPE_LABELS[type] || type
}

/**
 * 根据资源类型和ID跳转到对应详情页
 */
export function navigateToResource(router: { push: (path: string) => void }, type: string, id: number) {
  if (type === 'file') {
    router.push(`/file/${id}`)
  } else if (type === 'doc') {
    router.push(`/doc/${id}`)
  } else if (type === 'web') {
    router.push(`/web/${id}`)
  }
}
