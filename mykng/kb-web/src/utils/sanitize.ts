import DOMPurify from 'dompurify'

/**
 * 统一的 HTML 清洗工具，防止 v-html 渲染时发生存储型 XSS。
 *
 * 三种 profile：
 * - sanitizeWeb：用于网页收藏正文（保留常见展示标签，剥离 script/iframe/事件属性）
 * - sanitizeDoc：用于笔记富文本内容（保留 wangeditor 输出的常用标签）
 * - sanitizeTable：用于 xlsx sheet_to_html 输出（仅允许表格相关标签）
 */

const WEB_ALLOWED_TAGS = [
  'a', 'p', 'div', 'span', 'br', 'hr', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'b', 'strong', 'i', 'em', 'u', 's', 'del', 'blockquote', 'pre', 'code',
  'img', 'table', 'thead', 'tbody', 'tr', 'td', 'th', 'caption', 'colgroup', 'col',
  'figure', 'figcaption', 'article', 'section', 'header', 'footer', 'nav', 'aside',
]

const WEB_ALLOWED_ATTR = ['href', 'src', 'alt', 'title', 'class', 'target', 'rel', 'colspan', 'rowspan', 'width', 'height']

const TABLE_ALLOWED_TAGS = ['table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th', 'caption', 'colgroup', 'col', 'span', 'br', 'div']

/** 清洗网页收藏正文 HTML */
export function sanitizeWeb(html: string | undefined | null): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: WEB_ALLOWED_TAGS,
    ALLOWED_ATTR: WEB_ALLOWED_ATTR,
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'input', 'style', 'link', 'meta'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onsubmit', 'onfocus', 'onblur'],
  })
}

/** 清洗笔记富文本内容 */
export function sanitizeDoc(html: string | undefined | null): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'input', 'link', 'meta', 'style'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onsubmit', 'onfocus', 'onblur'],
  })
}

/** 清洗 xlsx sheet_to_html 输出 */
export function sanitizeTable(html: string | undefined | null): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: TABLE_ALLOWED_TAGS,
    ALLOWED_ATTR: ['colspan', 'rowspan', 'class', 'width', 'height'],
  })
}
