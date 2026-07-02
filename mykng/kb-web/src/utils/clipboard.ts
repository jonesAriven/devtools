/**
 * 剪贴板工具：优先使用现代 Clipboard API，不可用时回退到 execCommand
 * 兼容非安全上下文（HTTP）和旧版浏览器
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  if (!text) return false

  // 优先使用现代 Clipboard API（需要安全上下文 HTTPS 或 localhost）
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 权限被拒绝或写入失败，回退到 execCommand
    }
  }

  // 回退方案：使用 textarea + execCommand
  try {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.top = '-9999px'
    textarea.style.left = '-9999px'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(textarea)
    return ok
  } catch {
    return false
  }
}
