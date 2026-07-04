import type MarkdownIt from 'markdown-it'
import mermaid from 'mermaid'
import katex from 'katex'

mermaid.initialize({
  startOnLoad: false,
  theme: 'default',
  securityLevel: 'loose',
})

let mermaidIdCounter = 0

function generateMermaidId(): string {
  return `mermaid-${Date.now()}-${++mermaidIdCounter}`
}

export function setupMermaid(md: MarkdownIt) {
  const originalFence = md.renderer.rules.fence
  md.renderer.rules.fence = (tokens, idx, options, env, slf) => {
    const token = tokens[idx]
    const info = token.info.trim()
    const lang = info.split(/\s+/)[0]

    if (lang === 'mermaid') {
      const id = generateMermaidId()
      const code = token.content.trim()
      setTimeout(() => {
        try {
          mermaid.run({
            querySelector: `#${id}`,
          })
        } catch (e) {
          console.error('Mermaid render error:', e)
        }
      }, 0)
      return `<div class="mermaid-wrapper"><pre id="${id}" class="mermaid">${md.utils.escapeHtml(code)}</pre></div>`
    }

    return originalFence
      ? originalFence(tokens, idx, options, env, slf)
      : slf.renderToken(tokens, idx, options)
  }
}

const CALLOUT_TYPES: Record<string, { icon: string; label: string }> = {
  note: { icon: 'ℹ️', label: '注意' },
  tip: { icon: '💡', label: '提示' },
  warning: { icon: '⚠️', label: '警告' },
  error: { icon: '❌', label: '错误' },
  important: { icon: '❗', label: '重要' },
  info: { icon: 'ℹ️', label: '信息' },
  success: { icon: '✅', label: '成功' },
  danger: { icon: '⛔', label: '危险' },
  caution: { icon: '⚠️', label: '小心' },
  question: { icon: '❓', label: '问题' },
  help: { icon: '❔', label: '帮助' },
}

export function setupCallout(md: MarkdownIt) {
  md.block.ruler.before('blockquote', 'callout', (state, startLine, endLine, silent) => {
    const start = state.bMarks[startLine] + state.tShift[startLine]
    const max = state.eMarks[startLine]

    if (state.src.charCodeAt(start) !== 0x3E) return false

    const calloutMatch = /^\s*>\s*\[!(\w+)\]\s*(.*)?$/.exec(state.src.slice(start, max))
    if (!calloutMatch) return false

    if (silent) return true

    const calloutType = calloutMatch[1].toLowerCase()
    const calloutTitle = calloutMatch[2] || ''
    const calloutInfo = CALLOUT_TYPES[calloutType] || { icon: '📝', label: calloutType }
    const displayTitle = calloutTitle || calloutInfo.label

    let nextLine = startLine + 1

    while (nextLine < endLine) {
      const nextStart = state.bMarks[nextLine] + state.tShift[nextLine]
      const nextMax = state.eMarks[nextLine]
      if (nextStart >= nextMax) break
      if (state.src.charCodeAt(nextStart) !== 0x3E) break
      nextLine++
    }

    let content = ''
    for (let i = startLine + 1; i < nextLine; i++) {
      const lineStart = state.bMarks[i] + state.tShift[i]
      const lineMax = state.eMarks[i]
      let line = state.src.slice(lineStart, lineMax)
      if (line.startsWith('> ')) {
        line = line.slice(2)
      } else if (line.startsWith('>')) {
        line = line.slice(1)
      }
      content += line + '\n'
    }

    const renderedContent = md.render(content.trim())

    const token = state.push('callout', 'div', 0)
    token.attrSet('class', `callout callout-${calloutType}`)
    token.map = [startLine, nextLine]
    token.content = `
      <div class="callout-title">
        <span class="callout-icon">${calloutInfo.icon}</span>
        <span class="callout-title-text">${md.utils.escapeHtml(displayTitle)}</span>
      </div>
      <div class="callout-body">
        ${renderedContent}
      </div>
    `

    state.line = nextLine
    return true
  })

  md.renderer.rules.callout = (tokens: any[], idx: number) => {
    return tokens[idx].content
  }
}

export function setupKatex(md: MarkdownIt) {
  md.inline.ruler.after('escape', 'katex_inline', (state, silent) => {
    const src = state.src.slice(state.pos)
    if (!src.startsWith('$')) return false
    if (src.startsWith('$$')) return false

    const match = src.match(/^\$([^$\n]+?)\$/)
    if (!match) return false
    if (silent) return true

    const token = state.push('katex_inline', 'span', 0)
    token.markup = '$'
    token.content = match[1]

    state.pos += match[0].length
    return true
  })

  md.block.ruler.after('blockquote', 'katex_block', (state, startLine, endLine, silent) => {
    const start = state.bMarks[startLine] + state.tShift[startLine]
    const max = state.eMarks[startLine]

    if (state.src.slice(start, max).trim() !== '$$') return false
    if (silent) return true

    let nextLine = startLine + 1
    let content = ''

    while (nextLine < endLine) {
      const lineStart = state.bMarks[nextLine] + state.tShift[nextLine]
      const lineMax = state.eMarks[nextLine]
      const lineContent = state.src.slice(lineStart, lineMax).trim()

      if (lineContent === '$$') {
        nextLine++
        break
      }

      if (content) content += '\n'
      content += state.src.slice(lineStart, lineMax)
      nextLine++
    }

    const token = state.push('katex_block', 'div', 0)
    token.markup = '$$'
    token.content = content
    token.map = [startLine, nextLine]

    state.line = nextLine
    return true
  })

  md.renderer.rules.katex_inline = (tokens: any[], idx: number) => {
    const token = tokens[idx]
    try {
      const html = katex.renderToString(token.content, {
        throwOnError: false,
        displayMode: false,
      })
      return `<span class="katex-inline">${html}</span>`
    } catch (e) {
      return `<span class="katex-error">$${token.content}$</span>`
    }
  }

  md.renderer.rules.katex_block = (tokens: any[], idx: number) => {
    const token = tokens[idx]
    try {
      const html = katex.renderToString(token.content, {
        throwOnError: false,
        displayMode: true,
      })
      return `<div class="katex-block">${html}</div>`
    } catch (e) {
      return `<div class="katex-error">$$${token.content}$$</div>`
    }
  }
}

export function configureMarkdownIt(md: MarkdownIt) {
  setupMermaid(md)
  setupCallout(md)
  setupKatex(md)
}
