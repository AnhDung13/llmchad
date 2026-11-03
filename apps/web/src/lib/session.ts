// Lưu phiên đăng nhập (access token + refresh token) ở localStorage cho đơn giản.
// Đánh đổi: JS trên trang (kể cả script bị XSS chèn vào) đọc được token.
// Bản production thường để refresh token trong cookie httpOnly để JS không đọc được.

import type { TokenResponse } from './types'

const KEY = 'llmchad.session'

export type Session = {
  accessToken: string
  /** Thời điểm access token hết hạn, tính bằng ms (Date.now()) */
  accessExpiresAt: number
  /** null khi backend chưa trả refresh token: hết access token là phải đăng nhập lại */
  refreshToken: string | null
}

export function saveSession(tokens: TokenResponse, previousRefreshToken: string | null = null) {
  const session: Session = {
    accessToken: tokens.accessToken,
    accessExpiresAt: Date.now() + tokens.expiresIn * 1000,
    // Backend xoay vòng (rotate) thì trả refresh token mới; không trả thì giữ cái cũ
    refreshToken: tokens.refreshToken || previousRefreshToken,
  }
  localStorage.setItem(KEY, JSON.stringify(session))
}

export function getSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function clearSession() {
  localStorage.removeItem(KEY)
}
