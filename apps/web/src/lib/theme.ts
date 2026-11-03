// Chế độ sáng/tối: lưu lựa chọn ở localStorage, chưa chọn thì theo hệ điều hành.
// index.html có một đoạn script nhỏ gắn class "dark" trước khi React chạy để không bị nháy trắng.

import { useCallback, useSyncExternalStore } from 'react'

export type Theme = 'light' | 'dark'

const KEY = 'llmchad.theme'
const listeners = new Set<() => void>()

function current(): Theme {
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
}

function apply(theme: Theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
  document.documentElement.style.colorScheme = theme
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // trình duyệt chặn storage thì chỉ đổi cho lần mở này
  }
  listeners.forEach((l) => l())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function useTheme() {
  const theme = useSyncExternalStore(subscribe, current)
  const toggle = useCallback(() => apply(current() === 'dark' ? 'light' : 'dark'), [])
  return { theme, toggle }
}
