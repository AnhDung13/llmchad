import { useTranslation } from 'react-i18next'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router'
import { AuthPage } from './auth/AuthPage'
import { AuthProvider } from './auth/AuthProvider'
import { ForgotPasswordPage } from './auth/ForgotPasswordPage'
import { useAuth } from './auth/context'
import { OAuthCallbackPage } from './auth/OAuthCallbackPage'
import { ResetPasswordPage } from './auth/ResetPasswordPage'
import { ChatPage } from './chat/ChatPage'

function RequireAuth() {
  const { user, loading } = useAuth()
  const { t } = useTranslation('chat')
  if (loading) {
    return <div className="flex h-full items-center justify-center text-sm text-slate-500">{t('checkingAuth')}</div>
  }
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* key: đổi /login ↔ /register thì tạo lại form từ đầu (bỏ cả màn "kiểm tra email") */}
          <Route path="/login" element={<AuthPage key="login" mode="login" />} />
          <Route path="/register" element={<AuthPage key="register" mode="register" />} />
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          {/* Link trong mail đặt lại mật khẩu: /reset-password?token=... */}
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route element={<RequireAuth />}>
            {/* Một ChatPage dùng chung cho "/" và "/c/:id" để không bị mount lại khi đang stream */}
            <Route path="/" element={<ChatPage />}>
              <Route index element={null} />
              <Route path="c/:id" element={null} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
