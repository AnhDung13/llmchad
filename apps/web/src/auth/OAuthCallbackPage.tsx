import { LoaderCircleIcon } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage, translateMessage } from '../lib/i18n'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './context'

/**
 * /oauth/callback?code=...: backend chuyển về đây sau khi Google/GitHub xác nhận người dùng.
 * `code` là mã dùng 1 lần, sống ngắn; đổi nó lấy access/refresh token như khi đăng nhập thường.
 */
export function OAuthCallbackPage() {
  const { loginWithOAuthCode } = useAuth()
  const navigate = useNavigate()
  const { t } = useTranslation('auth')
  const [searchParams] = useSearchParams()
  const code = searchParams.get('code')
  // StrictMode chạy effect 2 lần lúc dev; mã chỉ dùng được 1 lần nên phải chặn lần gọi thứ hai
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    if (!code) {
      navigate('/login?oauthError=auth.oauthFailed', { replace: true })
      return
    }
    loginWithOAuthCode(code)
      .then((key) => {
        toast.success(key ? translateMessage(key) : t('loginSuccess'))
        navigate('/', { replace: true })
      })
      .catch((err: unknown) => {
        toast.error(errorMessage(err))
        navigate('/login', { replace: true })
      })
  }, [code, loginWithOAuthCode, navigate, t])

  return (
    <AuthLayout>
      <div className="flex items-center justify-center gap-2 py-6 text-sm text-slate-500 dark:text-slate-400">
        <LoaderCircleIcon size={18} className="animate-spin" />
        {t('oauthRedirecting')}
      </div>
    </AuthLayout>
  )
}
