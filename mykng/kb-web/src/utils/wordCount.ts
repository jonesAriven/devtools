/**
 * 字数统计工具
 * 参考 Notion/Yuque 的字数统计规则：
 * - 中文字符按 1 字计算
 * - 英文单词按 1 词计算
 * - 数字序列按 1 词计算
 * - 标点符号不计入
 */

export interface CountResult {
  /** 字数（中文字符 + 英文单词 + 数字序列） */
  words: number
  /** 字符数（含标点、空格） */
  characters: number
  /** 预计阅读时间（分钟） */
  readingTime: number
  /** 阅读时间文案 */
  readingTimeText: string
}

/** 阅读速度：中文 300 字/分钟（业界标准） */
const WORDS_PER_MINUTE = 300

/**
 * 统计纯文本的字数和阅读时间
 * @param text 纯文本内容（HTML 内容请先 stripHtml）
 */
export function countText(text: string): CountResult {
  if (!text) {
    return { words: 0, characters: 0, readingTime: 0, readingTimeText: '0 分钟' }
  }

  const characters = text.length

  // 中文字符（含全角标点）
  const chineseChars = text.match(/[\u4e00-\u9fa5]/g)?.length || 0
  // 英文单词
  const englishWords = text.match(/[a-zA-Z]+/g)?.length || 0
  // 数字序列
  const numberGroups = text.match(/\d+/g)?.length || 0

  const words = chineseChars + englishWords + numberGroups
  const readingTime = Math.max(1, Math.ceil(words / WORDS_PER_MINUTE))

  let readingTimeText: string
  if (words === 0) {
    readingTimeText = '0 分钟'
  } else if (readingTime < 1) {
    readingTimeText = '不到 1 分钟'
  } else {
    readingTimeText = `约 ${readingTime} 分钟`
  }

  return { words, characters, readingTime, readingTimeText }
}

/**
 * 去除 HTML 标签，返回纯文本
 */
export function stripHtml(html: string): string {
  if (!html) return ''
  // 创建临时 div 解析 HTML，再取 textContent 自动处理转义
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || div.innerText || ''
}

/**
 * 统计文档字数（支持 HTML 和纯文本）
 */
export function countDocumentContent(content: string, format: 'html' | 'markdown'): CountResult {
  if (!content) {
    return { words: 0, characters: 0, readingTime: 0, readingTimeText: '0 分钟' }
  }
  let text = content
  if (format === 'html') {
    text = stripHtml(content)
  } else {
    // Markdown：去除标记符号再统计
    text = content
      // 移除代码块
      .replace(/```[\s\S]*?```/g, ' ')
      // 移除行内代码
      .replace(/`[^`]+`/g, ' ')
      // 移除图片
      .replace(/!\[.*?\]\(.*?\)/g, ' ')
      // 移除链接，保留文本
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      // 移除标题井号
      .replace(/^#{1,6}\s+/gm, '')
      // 移除粗体/斜体标记
      .replace(/[*_]{1,3}([^*_]+)[*_]{1,3}/g, '$1')
      // 移除引用标记
      .replace(/^>\s+/gm, '')
      // 移除列表标记
      .replace(/^[-*+]\s+/gm, '')
      // 移除有序列表标记
      .replace(/^\d+\.\s+/gm, '')
      // 移除双向链接语法 [[id|title]] -> title
      .replace(/\[\[\d+\|([^\]]+)\]\]/g, '$1')
      // 移除 HTML 标签
      .replace(/<[^>]+>/g, '')
  }
  return countText(text)
}
