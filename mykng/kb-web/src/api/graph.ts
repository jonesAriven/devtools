import request from './index'
import type { R, Doc } from '@/types'

export interface GraphNode {
  id: string
  name: string
  value: number
  category: number
  docId: number
  symbolSize: number
}

export interface GraphLink {
  source: string
  target: string
}

export interface GraphData {
  nodes: GraphNode[]
  links: GraphLink[]
  categories: { name: string }[]
}

export async function fetchAllDocs(): Promise<Doc[]> {
  const allDocs: Doc[] = []
  let page = 1
  const size = 100

  while (true) {
    const res = await request.get<R<{ list: Doc[]; total: number }>>('/doc/list', {
      params: { page, size },
    })
    const list = res.data.data?.list || []
    allDocs.push(...list)
    if (list.length < size || allDocs.length >= (res.data.data?.total || 0)) {
      break
    }
    page++
  }

  return allDocs
}

function parseWikiLinks(content: string): string[] {
  const regex = /\[\[(\d+)\]\]/g
  const matches: string[] = []
  let match
  while ((match = regex.exec(content)) !== null) {
    matches.push(match[1])
  }
  return [...new Set(matches)]
}

export function buildGraphFromDocs(docs: Doc[]): GraphData {
  const docMap = new Map<number, Doc>()
  docs.forEach((doc) => docMap.set(doc.id, doc))

  const linkCountMap = new Map<number, number>()
  const links: GraphLink[] = []
  const linkSet = new Set<string>()

  docs.forEach((doc) => {
    const linkedIds = parseWikiLinks(doc.content || '')
    linkedIds.forEach((idStr) => {
      const targetId = parseInt(idStr, 10)
      if (docMap.has(targetId) && targetId !== doc.id) {
        const linkKey = [Math.min(doc.id, targetId), Math.max(doc.id, targetId)].join('-')
        if (!linkSet.has(linkKey)) {
          linkSet.add(linkKey)
          links.push({
            source: String(doc.id),
            target: String(targetId),
          })
          linkCountMap.set(doc.id, (linkCountMap.get(doc.id) || 0) + 1)
          linkCountMap.set(targetId, (linkCountMap.get(targetId) || 0) + 1)
        }
      }
    })
  })

  const maxLinkCount = Math.max(...Array.from(linkCountMap.values()), 1)
  const minSize = 30
  const maxSize = 80

  const nodes: GraphNode[] = docs.map((doc) => {
    const linkCount = linkCountMap.get(doc.id) || 0
    const size = minSize + (maxSize - minSize) * (linkCount / maxLinkCount)
    return {
      id: String(doc.id),
      name: doc.title,
      value: linkCount,
      category: 0,
      docId: doc.id,
      symbolSize: size,
    }
  })

  return {
    nodes,
    links,
    categories: [{ name: '文档' }],
  }
}
