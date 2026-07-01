/**
 * 文档大纲提取工具
 * 从 HTML 或 Markdown 内容中提取标题（h1-h6 / # ## ###）
 */

export interface OutlineItem {
  id: string
  text: string
  level: number // 1-6
}

/**
 * 从 Markdown 提取标题大纲
 */
function extractMarkdownOutline(content: string): OutlineItem[] {
  const items: OutlineItem[] = []
  // 匹配 ATX 风格标题：# Title / ## Title
  const regex = /^#{1,6}\s+(.+?)\s*#*\s*$/gm
  let match: RegExpExecArray | null
  let idx = 0
  while ((match = regex.exec(content)) !== null) {
    const hashes = match[0].match(/^#+/)![0]
    items.push({
      id: `md-heading-${idx++}`,
      text: match[1].trim(),
      level: hashes.length,
    })
  }
  return items
}

/**
 * 从 HTML 提取标题大纲
 */
function extractHtmlOutline(content: string): OutlineItem[] {
  const items: OutlineItem[] = []
  const container = document.createElement('div')
  container.innerHTML = content
  const headings = container.querySelectorAll('h1, h2, h3, h4, h5, h6')
  headings.forEach((h, idx) => {
    const level = parseInt(h.tagName.substring(1), 10)
    // 给没有 id 的标题生成 id，便于点击跳转
    let hid = h.getAttribute('id')
    if (!hid) {
      hid = `heading-${idx}`
      h.setAttribute('id', hid)
    }
    items.push({
      id: hid,
      text: h.textContent || '',
      level,
    })
  })
  return items
}

/**
 * 根据 format 提取大纲
 */
export function extractOutline(content: string, format: 'html' | 'markdown'): OutlineItem[] {
  if (!content) return []
  try {
    return format === 'markdown' ? extractMarkdownOutline(content) : extractHtmlOutline(content)
  } catch (e) {
    console.warn('提取大纲失败', e)
    return []
  }
}

/**
 * 滚动到指定标题
 */
export function scrollToHeading(id: string) {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}
