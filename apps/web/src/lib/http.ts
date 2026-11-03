// Gọi API backend bằng ky: gắn Authorization: Bearer, mở envelope { status, message, data },
// tự gọi POST /api/auth/refresh khi access token hết hạn.
// Mọi lỗi ra khỏi file này đều là ApiError (trừ AbortError khi người dùng huỷ request).

import ky, { isHTTPError, isNetworkError, isTimeoutError, type KyRequest, type Options } from 'ky'
import { ApiError } from './errors'
import { clearSession, getSession, saveSession } from './session'
import type { ApiEnvelope, TokenResponse } from './types'

export { ApiError } from './errors'

/**
 * Trình duyệt luôn gọi /api trên cùng origin với Vite.
 * Lúc dev, Vite chuyển tiếp /api/* sang VITE_API_URL (xem vite.config.ts), nên backend không cần CORS.
 */
const API_BASE = '/api'
const BACKEND_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/** Làm mới sớm hơn hạn thật một chút, để token không hết hạn giữa đường. */
const EXPIRY_SKEW_MS = 30_000

let onSessionExpired: () => void = () => {}

/** AuthProvider đăng ký hàm này để đưa người dùng về trang đăng nhập khi phiên hết hạn. */
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

function expireSession() {
  clearSession()
  onSessionExpired()
}

/** Dữ liệu riêng của mình truyền cho hook của ky qua option `context` */
type RequestContext = {
  /** false cho các endpoint không cần đăng nhập (login, register, refresh, logout) */
  auth?: boolean
  /** Token vừa làm mới, dùng thay cho token trong session khi gửi lại request */
  token?: string
}

const client = ky.create({
  prefix: API_BASE,
  headers: { Accept: 'application/json' },
  // TanStack Query lo việc thử lại; ở đây không tự retry để tránh gửi tin nhắn hai lần
  retry: 0,
  hooks: {
    beforeRequest: [
      async ({ request, options }) => {
        const ctx = options.context as RequestContext
        if (ctx.auth === false) return
        const token = ctx.token ?? (await getFreshAccessToken())
        if (token) request.headers.set('Authorization', `Bearer ${token}`)
      },
    ],
  },
})

type RequestOptions = {
  /** false cho các endpoint không cần đăng nhập (login, register, refresh, logout) */
  auth?: boolean
}

export async function request(path: string, options: Options = {}, { auth = true }: RequestOptions = {}): Promise<Response> {
  try {
    try {
      return await client(path, { ...options, context: { auth } satisfies RequestContext })
    } catch (err) {
      // Token bị từ chối (ví dụ backend khởi động lại với khoá JWT khác) → thử làm mới một lần rồi gửi lại
      const stale = auth && isHTTPError(err) && err.response.status === 401 ? bearerToken(err.request) : null
      if (!stale) throw err
      const token = await refreshAccessToken(stale)
      if (!token) throw err
      return await client(path, { ...options, context: { auth, token } satisfies RequestContext })
    }
  } catch (err) {
    throw toApiError(err, auth)
  }
}

/** Cả envelope, khi cần đọc thêm `message` (ví dụ "auth.loginSuccess" lúc đăng nhập) */
export async function envelope<T>(path: string, options?: Options, requestOptions?: RequestOptions): Promise<ApiEnvelope<T>> {
  const res = await request(path, options, requestOptions)
  return (await res.json()) as ApiEnvelope<T>
}

export async function json<T>(path: string, options?: Options, requestOptions?: RequestOptions): Promise<T> {
  return (await envelope<T>(path, options, requestOptions)).data
}

/** Lấy dữ liệu thật trong field `data` của envelope */
export async function readBody<T>(res: Response): Promise<T> {
  return ((await res.json()) as ApiEnvelope<T>).data
}

function bearerToken(req: KyRequest): string | null {
  return req.headers.get('Authorization')?.replace(/^Bearer /, '') ?? null
}

// ---------------------------------------------------------------------------
// Refresh token

/** Access token còn hạn thì dùng luôn, sắp hết hạn thì làm mới trước khi gửi request. */
async function getFreshAccessToken(): Promise<string | null> {
  const session = getSession()
  if (!session) return null
  if (session.accessExpiresAt - EXPIRY_SKEW_MS > Date.now()) return session.accessToken
  return refreshAccessToken(session.accessToken)
}

let refreshing: Promise<string | null> | null = null

/**
 * Trả về access token mới, hoặc null nếu không làm mới được (phải đăng nhập lại).
 * Nhiều request cùng gặp token hết hạn chỉ gọi /auth/refresh một lần: backend xoay vòng refresh token,
 * nên gọi hai lần với cùng một refresh token thì lần thứ hai sẽ bị từ chối.
 */
function refreshAccessToken(staleToken: string): Promise<string | null> {
  refreshing ??= withRefreshLock(() => doRefresh(staleToken)).finally(() => {
    refreshing = null
  })
  return refreshing
}

/** Khoá dùng chung giữa các tab, để hai tab không làm mới cùng lúc bằng cùng một refresh token. */
function withRefreshLock<T>(fn: () => Promise<T>): Promise<T> {
  return 'locks' in navigator ? navigator.locks.request('llmchat.refresh', fn) : fn()
}

async function doRefresh(staleToken: string): Promise<string | null> {
  const session = getSession()
  if (!session) return null

  // Tab khác (hoặc lần gọi trước) đã làm mới xong → dùng token mới luôn
  if (session.accessToken !== staleToken && session.accessExpiresAt - EXPIRY_SKEW_MS > Date.now()) {
    return session.accessToken
  }
  if (!session.refreshToken) return null

  let res: Response
  try {
    res = await client.post('auth/refresh', {
      json: { refreshToken: session.refreshToken },
      context: { auth: false } satisfies RequestContext,
      throwHttpErrors: false,
    })
  } catch (err) {
    throw toApiError(err, false)
  }
  if (res.status === 400 || res.status === 401) {
    // Refresh token hết hạn, đã bị thu hồi hoặc backend chưa có endpoint này
    clearSession()
    return null
  }
  if (!res.ok) {
    // Lỗi server hoặc mạng: giữ phiên, lần sau thử lại
    throw new ApiError(res.status, readMessage(await res.json().catch(() => undefined)))
  }

  const tokens = await readBody<TokenResponse>(res)
  saveSession(tokens, session.refreshToken)
  return tokens.accessToken
}

// ---------------------------------------------------------------------------
// Lỗi

function toApiError(err: unknown, auth: boolean): unknown {
  if (err instanceof ApiError) return err
  if (err instanceof DOMException && err.name === 'AbortError') return err
  if (isHTTPError(err)) {
    const status = err.response.status
    // 401 ở endpoint không cần đăng nhập (ví dụ sai mật khẩu) không có nghĩa là phiên hết hạn
    if (status === 401 && auth) {
      expireSession()
      return new ApiError(401, 'errors.sessionExpired')
    }
    return new ApiError(status, readMessage(err.data))
  }
  if (isNetworkError(err)) return new ApiError(0, 'errors.network', { url: BACKEND_URL })
  if (isTimeoutError(err)) return new ApiError(504, '')
  return err
}

/**
 * Đọc thông báo lỗi: ưu tiên `message` của envelope (message key hoặc câu chữ thô).
 * Vẫn đọc được Problem Details (`detail`/`title`) phòng khi một lỗi nào đó của Spring chưa được bọc envelope.
 * Rỗng → errorMessage() dùng câu mặc định theo HTTP status (locales/common.json).
 */
function readMessage(data: unknown): string {
  if (typeof data !== 'object' || data === null) return ''
  const body = data as { message?: string | null; detail?: string; title?: string }
  return body.message || body.detail || body.title || ''
}
