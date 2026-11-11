import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../lib/api'
import { ApiError, setSessionExpiredHandler } from '../lib/http'
import { keys } from '../lib/queryClient'
import { clearSession, getSession, saveSession } from '../lib/session'
import type { LoginInput, LoginResponse, RegisterInput } from '../lib/types'
import { AuthContext, type AuthState } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  // Trình duyệt đang giữ phiên hay không; đổi khi đăng nhập, đăng xuất hoặc phiên hết hạn
  const [hasSession, setHasSession] = useState(() => getSession() !== null)

  // Mở trang mà đã có phiên → hỏi backend xem còn dùng được không (GET /api/me).
  // Access token hết hạn thì lib/http tự gọi /api/auth/refresh trước.
  const me = useQuery({
    queryKey: keys.me,
    queryFn: ({ signal }) => api.me(signal),
    enabled: hasSession,
    staleTime: Infinity,
    retry: false,
  })

  // 401 thì lib/http đã xoá phiên. Backend chưa chạy (lỗi mạng, hoặc lúc dev proxy của Vite trả 502/503/504)
  // thì giữ phiên để lần sau thử lại. Lỗi khác thì xoá phiên để đăng nhập lại.
  useEffect(() => {
    const err = me.error
    if (err instanceof ApiError && err.status !== 0 && err.status !== 401 && err.status < 502) clearSession()
  }, [me.error])

  useEffect(() => {
    setSessionExpiredHandler(() => setHasSession(false))
  }, [])

  const logout = useCallback(() => {
    const refreshToken = getSession()?.refreshToken
    clearSession()
    setHasSession(false)
    // Bỏ hết dữ liệu của người dùng này khỏi cache
    queryClient.clear()
    // Báo backend thu hồi refresh token; lỗi cũng không sao vì phía trình duyệt đã xoá phiên
    if (refreshToken) api.logout(refreshToken).catch(() => {})
  }, [queryClient])

  /** Lưu token và user vừa nhận từ /auth/login hoặc /auth/oauth/exchange */
  const startSession = useCallback(
    (data: LoginResponse) => {
      saveSession(data)
      // Bỏ dữ liệu còn sót của phiên trước, nhưng giữ query 'me': useQuery ở trên đang bám vào đúng object Query đó.
      // Xoá đi rồi tạo lại thì useQuery vẫn nhìn object cũ (đang lỗi) cho tới khi component render lại → kẹt ở /login.
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== keys.me[0] })
      queryClient.setQueryData(keys.me, data.user) // đã có user → không cần gọi /api/me
      setHasSession(true)
    },
    [queryClient],
  )

  const login = useCallback(
    async (input: LoginInput) => {
      const res = await api.login(input)
      startSession(res.data)
      return res.message
    },
    [startSession],
  )

  const loginWithOAuthCode = useCallback(
    async (code: string) => {
      const res = await api.exchangeOAuthCode(code)
      startSession(res.data)
      return res.message
    },
    [startSession],
  )

  // Chưa đăng nhập được: backend chỉ tạo tài khoản sau khi nhập đúng mã xác minh (verifyEmail)
  const register = useCallback(async (input: RegisterInput) => {
    const res = await api.register(input)
    return { message: res.message, verification: res.data }
  }, [])

  const verifyEmail = useCallback(
    async (email: string, password: string, code: string) => {
      const res = await api.verifyEmail(email, password, code)
      startSession(res.data)
      return res.message
    },
    [startSession],
  )

  const user = hasSession ? (me.data ?? null) : null
  const loading = hasSession && me.isPending

  const value = useMemo<AuthState>(
    () => ({ user, loading, login, loginWithOAuthCode, register, verifyEmail, logout }),
    [user, loading, login, loginWithOAuthCode, register, verifyEmail, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
