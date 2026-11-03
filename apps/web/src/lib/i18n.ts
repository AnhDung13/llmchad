// Đa ngôn ngữ (vi/en) bằng i18next. Mỗi nhóm màn hình một namespace = một file JSON trong src/locales/<lang>/:
//   common  – lỗi chung, tên ngôn ngữ          server  – message key backend trả về (khớp MessageKey.java)
//   auth    – trang đăng nhập/đăng ký           chat    – ChatPage, App
//   sidebar – Sidebar                           thread  – MessageList, Composer, Markdown
// Trong component: const { t } = useTranslation('chat'); t('emptyTitle')

import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import enAuth from '../locales/en/auth.json'
import enChat from '../locales/en/chat.json'
import enCommon from '../locales/en/common.json'
import enServer from '../locales/en/server.json'
import enSidebar from '../locales/en/sidebar.json'
import enThread from '../locales/en/thread.json'
import viAuth from '../locales/vi/auth.json'
import viChat from '../locales/vi/chat.json'
import viCommon from '../locales/vi/common.json'
import viServer from '../locales/vi/server.json'
import viSidebar from '../locales/vi/sidebar.json'
import viThread from '../locales/vi/thread.json'
import { ApiError } from './errors'

export const LANGUAGES = ['vi', 'en'] as const
export type Language = (typeof LANGUAGES)[number]

const KEY = 'llmchad.lang'

function initialLanguage(): Language {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'vi' || saved === 'en') return saved
  } catch {
    // storage bị chặn → dùng mặc định
  }
  return 'vi'
}

const lng = initialLanguage()

void i18n.use(initReactI18next).init({
  resources: {
    vi: { common: viCommon, server: viServer, auth: viAuth, chat: viChat, sidebar: viSidebar, thread: viThread },
    en: { common: enCommon, server: enServer, auth: enAuth, chat: enChat, sidebar: enSidebar, thread: enThread },
  },
  lng,
  fallbackLng: 'vi',
  defaultNS: 'common',
  interpolation: { escapeValue: false }, // React đã tự escape
})
document.documentElement.lang = lng

export function setLanguage(next: Language) {
  void i18n.changeLanguage(next)
  document.documentElement.lang = next
  try {
    localStorage.setItem(KEY, next)
  } catch {
    // chỉ đổi cho lần mở này
  }
}

const MESSAGE_NS = ['server', 'common']

/**
 * Dịch message key của backend (server.json) hoặc của frontend (common.json).
 * Không phải key (backend còn trả câu viết cứng) thì giữ nguyên.
 */
export function translateMessage(message: string, params?: Record<string, unknown>): string {
  return i18n.exists(message, { ns: MESSAGE_NS }) ? i18n.t(message, { ns: MESSAGE_NS, ...params }) : message
}

/** Câu thông báo lỗi để hiển thị (toast, khung báo lỗi) */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.message) return translateMessage(err.message, err.params)
    const key = `errors.status.${err.status}`
    return i18n.exists(key) ? i18n.t(key) : i18n.t('errors.status.default', { status: err.status })
  }
  if (err instanceof Error && err.message) return translateMessage(err.message)
  return i18n.t('errors.unknown')
}

export default i18n
