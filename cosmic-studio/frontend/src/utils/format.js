// 全站时间列统一格式化。后端返回无时区标记的 ISO 串（2026-08-29T14:36:50），
// 直接绑到表格列会把字母 T 原样渲染到界面；所有 created_at 类列统一走这里。
// 兼容 el-table 的 :formatter 直绑（签名 row/column/cellValue）与直接传值两种用法。
export function formatDateTime(row, column, cellValue) {
  const v = cellValue ?? row
  if (!v) return '—'
  const d = new Date(v)
  if (isNaN(d.getTime())) return String(v)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
