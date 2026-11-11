// Các endpoint của backend, khớp với docs/api-contract.md

import type { Options } from 'ky'
import { ApiError, envelope, json, readBody, request } from './http'
import { readSse } from './sse'
import type {
  ChatEvent,
  Conversation,
  LoginInput,
  LoginResponse,
  Message,
  RegisterInput,
  ResetLinkSentResponse,
  User,
  VerificationCodeResponse,
} from './types'

const post = (body: unknown): Options => ({ method: 'post', json: body })

export const api = {
  /** Trả cả envelope để đọc `message` (vd "auth.registerSuccess") */
  register: (input: RegisterInput) =>
    envelope<VerificationCodeResponse>('auth/register', post(input), { auth: false }),

  /** Trả cả envelope để đọc `message` (vd "auth.loginSuccess") */
  login: (input: LoginInput) => envelope<LoginResponse>('auth/login', post(input), { auth: false }),

  /** Đổi mã dùng 1 lần (backend gắn vào /oauth/callback?code=...) lấy token, giống hệt login */
  exchangeOAuthCode: (code: string) =>
    envelope<LoginResponse>('auth/oauth/exchange', post({ code }), { auth: false }),

  /** Mã 6 ký tự trong email. Đúng mã thì backend tạo tài khoản và đăng nhập luôn (response như login) */
  /** `password` là mật khẩu vừa nhập ở form đăng ký: backend kiểm tra để không ai đăng ký đè được */
  verifyEmail: (email: string, password: string, code: string) =>
    envelope<LoginResponse>('auth/verify-email', post({ email, password, code }), { auth: false }),

  resendVerification: (email: string) =>
    envelope<VerificationCodeResponse>('auth/resend-verification', post({ email }), { auth: false }),

  /** Luôn thành công dù email chưa đăng ký (backend không cho dò email). Gửi lại link = gọi lại hàm này */
  forgotPassword: (email: string) =>
    envelope<ResetLinkSentResponse>('auth/forgot-password', post({ email }), { auth: false }),

  /** Link còn dùng được không; lỗi "auth.resetTokenInvalid" (410) nếu đã dùng/hết hạn/bị thay bằng link mới */
  validateResetToken: async (token: string) => {
    await request('auth/reset-password/validate', post({ token }), { auth: false })
  },

  /** Đặt mật khẩu mới. Backend đăng xuất mọi thiết bị đang đăng nhập tài khoản này */
  resetPassword: (token: string, newPassword: string) =>
    envelope<null>('auth/reset-password', post({ token, newPassword }), { auth: false }),

  /** Thu hồi refresh token ở backend */
  logout: async (refreshToken: string) => {
    await request('auth/logout', post({ refreshToken }), { auth: false })
  },

  me: (signal?: AbortSignal) => json<User>('me', { signal }),

  /** Phân trang: `page` đếm từ 1, `size` tối đa 25. Sidebar hiện chỉ lấy trang đầu. */
  listConversations: (signal?: AbortSignal, page = 1, size = 20) =>
    json<Conversation[]>('conversations', { searchParams: { page, size }, signal }),

  /** `title` bắt buộc, 1–200 ký tự */
  createConversation: (title: string) => json<Conversation>('conversations', post({ title })),

  /** `title` bắt buộc, 1–200 ký tự */
  renameConversation: (id: string, title: string) =>
    json<Conversation>(`conversations/${id}`, { method: 'put', json: { title } }),

  deleteConversation: async (id: string) => {
    await request(`conversations/${id}`, { method: 'delete' })
  },

  listMessages: (conversationId: string, signal?: AbortSignal) =>
    json<Message[]>(`conversations/${conversationId}/messages`, { signal }),

  async *sendMessage(conversationId: string, content: string, signal: AbortSignal): AsyncGenerator<ChatEvent> {
    const res = await request(`conversations/${conversationId}/stream`, {
      ...post({ content }),
      headers: { Accept: 'text/event-stream, application/json' },
      signal,
      // LLM có thể trả lời lâu: không giới hạn thời gian chờ
      timeout: false,
    })

    // Kiểu A: backend trả JSON một lần
    if (!res.headers.get('Content-Type')?.includes('text/event-stream')) {
      const body = await readBody<{ assistantMessage: Message }>(res)
      yield { type: 'done', message: body.assistantMessage }
      return
    }

    // Kiểu B: SSE
    if (!res.body) throw new ApiError(0, 'errors.streamUnsupported')
    for await (const ev of readSse(res.body)) {
      if (ev.event === 'reasoning' || ev.event === 'delta') {
        yield { type: ev.event, text: (JSON.parse(ev.data) as { text: string }).text }
      } else if (ev.event === 'done') {
        yield { type: 'done', message: JSON.parse(ev.data) as Message }
        return
      } else if (ev.event === 'error') {
        // `message` có thể là message key (vd "llm.failed") hoặc câu chữ thô
        throw new ApiError(502, (JSON.parse(ev.data) as { message?: string }).message ?? '')
      }
    }
    throw new ApiError(0, 'errors.streamNoDone')
  },
}
