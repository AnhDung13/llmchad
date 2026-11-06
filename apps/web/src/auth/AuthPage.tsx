import { EyeIcon, EyeOffIcon, LockIcon, MailIcon, UserIcon } from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage, translateMessage } from '../lib/i18n'
import type { OAuthProvider, VerificationCodeResponse } from '../lib/types'
import { GitHubIcon, GoogleIcon } from '../ui/icons'
import { AuthLayout } from './AuthLayout'
import { CheckEmailPanel } from './CheckEmailPanel'
import { useAuth } from './context'

type Mode = 'login' | 'register'

export function AuthPage({ mode }: { mode: Mode }) {
  const { user, login, register } = useAuth()
  const navigate = useNavigate()
  const { t } = useTranslation('auth')
  const [searchParams, setSearchParams] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  // Email vừa đăng ký, đang chờ nhập mã xác minh
  const [pending, setPending] = useState<{ email: string; verification: VerificationCodeResponse } | null>(null)

  // Đăng nhập Google/GitHub thất bại → backend chuyển về /login?oauthError=<message key>
  const oauthError = searchParams.get('oauthError')
  useEffect(() => {
    if (!oauthError) return
    toast.error(translateMessage(oauthError))
    setSearchParams({}, { replace: true })
  }, [oauthError, setSearchParams])

  if (user) return <Navigate to="/" replace />

  const isRegister = mode === 'register'

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    try {
      if (isRegister) {
        const { message, verification } = await register({ email, password, displayName })
        toast.success(message ? translateMessage(message) : t('registerSuccess'))
        setPending({ email, verification })
      } else {
        const key = await login({ email, password })
        toast.success(key ? translateMessage(key) : t('loginSuccess'))
        navigate('/', { replace: true })
      }
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (pending) {
    return (
      <AuthLayout>
        <CheckEmailPanel
          email={pending.email}
          password={password}
          initialVerification={pending.verification}
          onVerified={() => navigate('/', { replace: true })}
          // Mã hết hạn hoặc sai quá nhiều lần → quay lại form đăng ký (vẫn giữ email, tên đã nhập)
          onExpired={() => setPending(null)}
        />
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <h2 className="mb-5 text-lg font-semibold">{t(isRegister ? 'registerTitle' : 'loginTitle')}</h2>

      <form onSubmit={onSubmit} className="space-y-3">
        {isRegister && (
          <Field icon={<UserIcon size={16} />}>
            <input
              className={inputClass}
              placeholder={t('displayName')}
              aria-label={t('displayName')}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              maxLength={50}
              autoComplete="name"
              autoFocus
            />
          </Field>
        )}
        <Field icon={<MailIcon size={16} />}>
          <input
            className={inputClass}
            type="email"
            placeholder={t('emailPlaceholder')}
            aria-label={t('email')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            autoFocus={!isRegister}
          />
        </Field>
        <Field
          icon={<LockIcon size={16} />}
          trailing={
            <button
              type="button"
              onClick={() => setShowPassword((s) => !s)}
              className="rounded-md p-1 text-slate-400 transition hover:text-slate-700 dark:hover:text-slate-200"
              aria-label={t(showPassword ? 'hidePassword' : 'showPassword')}
            >
              {showPassword ? <EyeIcon size={16} /> : <EyeOffIcon size={16} />}
            </button>
          }
        >
          <input
            className={`${inputClass} pr-10`}
            type={showPassword ? 'text' : 'password'}
            placeholder={t(isRegister ? 'passwordPlaceholderRegister' : 'passwordPlaceholder')}
            aria-label={t('password')}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={isRegister ? 8 : undefined}
            autoComplete={isRegister ? 'new-password' : 'current-password'}
          />
        </Field>

        {!isRegister && (
          <div className="flex justify-end pt-1 text-sm">
            <span className="cursor-not-allowed text-indigo-500/70" title={t('notSupported')}>
              {t('forgotPassword')}
            </span>
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="mt-1 w-full rounded-lg bg-gradient-to-r from-indigo-500 to-violet-500 px-4 py-2.5 text-sm font-medium text-white shadow-md shadow-indigo-500/25 transition hover:from-indigo-600 hover:to-violet-600 disabled:opacity-60"
        >
          {t(submitting ? 'submitting' : isRegister ? 'register' : 'login')}
        </button>
      </form>

      <div className="my-5 flex items-center gap-3 text-xs text-slate-400">
        <span className="h-px flex-1 bg-slate-200 dark:bg-night-700" />
        {t(isRegister ? 'orRegisterWith' : 'orLoginWith')}
        <span className="h-px flex-1 bg-slate-200 dark:bg-night-700" />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <OAuthButton provider="google" icon={<GoogleIcon size={16} />} label="Google" />
        <OAuthButton provider="github" icon={<GitHubIcon size={16} />} label="GitHub" />
      </div>

      <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
        {t(isRegister ? 'hasAccount' : 'noAccount')}
        <Link
          to={isRegister ? '/login' : '/register'}
          className="font-medium text-indigo-600 hover:underline dark:text-indigo-400"
        >
          {t(isRegister ? 'login' : 'registerNow')}
        </Link>
      </p>
    </AuthLayout>
  )
}

const inputClass =
  'w-full rounded-lg border border-slate-200 bg-white py-2.5 pr-3 pl-10 text-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 dark:border-night-700 dark:bg-night-900 dark:placeholder:text-slate-500'

function Field({ icon, trailing, children }: { icon: ReactNode; trailing?: ReactNode; children: ReactNode }) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-slate-400">{icon}</span>
      {children}
      {trailing && <span className="absolute top-1/2 right-2 -translate-y-1/2">{trailing}</span>}
    </div>
  )
}

/** Bật khi backend có OAuth (docs/api-contract.md → Bước 9). Chưa có mà bấm thì chỉ ra trang lỗi 401. */
const OAUTH_ENABLED = false

/** Chuyển cả trang sang backend (không dùng fetch): backend redirect tiếp sang Google/GitHub */
function OAuthButton({ provider, icon, label }: { provider: OAuthProvider; icon: ReactNode; label: string }) {
  const { t } = useTranslation('auth')
  const className =
    'flex items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white py-2 text-sm font-medium text-slate-700 transition dark:border-night-700 dark:bg-night-900 dark:text-slate-200'
  if (!OAUTH_ENABLED) {
    return (
      <button type="button" disabled title={t('notSupported')} className={`${className} disabled:cursor-not-allowed`}>
        {icon}
        {label}
      </button>
    )
  }
  return (
    <a href={`/api/oauth2/authorization/${provider}`} className={`${className} hover:bg-slate-50 dark:hover:bg-night-800`}>
      {icon}
      {label}
    </a>
  )
}
