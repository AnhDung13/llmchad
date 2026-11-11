import { createContext, use } from 'react'
import type { LoginInput, RegisterInput, User, VerificationCodeResponse } from '../lib/types'

export type AuthState = {
  user: User | null
  /** true trong lúc kiểm tra token đã lưu khi mở trang */
  loading: boolean
  /** Trả về message key backend gửi kèm (vd "auth.loginSuccess"), null nếu không có */
  login(input: LoginInput): Promise<string | null>
  /** Đăng nhập bằng mã dùng 1 lần mà backend gửi về sau khi đăng nhập Google/GitHub */
  loginWithOAuthCode(code: string): Promise<string | null>
  /**
   * Chỉ gửi mã xác minh tới email, chưa tạo tài khoản: phải gọi verifyEmail với mã đó.
   * Trả về message key (vd "auth.registerSuccess") và thời hạn của mã để đếm ngược.
   */
  register(input: RegisterInput): Promise<{ message: string | null; verification: VerificationCodeResponse }>
  /** Nhập đúng mã thì backend tạo tài khoản và đăng nhập luôn */
  verifyEmail(email: string, password: string, code: string): Promise<string | null>
  logout(): void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = use(AuthContext)
  if (!ctx) throw new Error('useAuth phải dùng bên trong <AuthProvider>')
  return ctx
}
