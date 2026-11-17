import type { Source } from './types'

/** Tiêu đề (title) của link markdown đánh dấu đây là citation, kèm số nguồn: [giavang.org](<url> "cite:1") */
export const CITE_TITLE_PREFIX = 'cite:'

export function hostOf(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

// Model trích dẫn bằng [1], [1, 3] hoặc kiểu riêng của gpt-oss: 【1】, 【1†L5-L9】.
// Bỏ qua [1](...) vì đó là link markdown, và ![1] vì đó là ảnh.
const MARKER = /(?<!!)\[(\d+(?:\s*,\s*\d+)*)\](?!\()|【(\d+)(?:†[^】]*)?】/g
// Khối code (``` … ```, kể cả khối chưa đóng khi đang stream) và inline code: không đụng vào, vd. arr[1]
const CODE = /(```[\s\S]*?(?:```|$)|`[^`\n]*`)/

/**
 * Đổi các số trích dẫn trong câu trả lời thành link markdown tới nguồn tương ứng (sources[n - 1]).
 * Số nằm ngoài danh sách nguồn thì giữ nguyên chữ.
 */
export function linkCitations(text: string, sources: Source[] | undefined): string {
  if (!sources || sources.length === 0) return text
  return text
    .split(CODE)
    .map((part, i) => (i % 2 === 1 ? part : part.replace(MARKER, (marker, list?: string, single?: string) => {
      const ids = (list ?? single ?? '').split(',').map((n) => Number(n.trim()))
      if (ids.some((n) => !Number.isInteger(n) || n < 1 || n > sources.length)) return marker
      return ids.map((n) => citationLink(n, sources[n - 1])).join('')
    })))
    .join('')
}

function citationLink(n: number, source: Source) {
  // <...> cho phép URL có ngoặc đơn; khoảng trắng và < > phải encode
  const url = source.url.replace(/[\s<>]/g, encodeURIComponent)
  return ` [${hostOf(source.url)}](<${url}> "${CITE_TITLE_PREFIX}${n}")`
}
