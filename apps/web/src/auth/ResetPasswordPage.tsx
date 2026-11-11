import { EyeIcon, EyeOffIcon, LinkIcon, LoaderCircleIcon, LockIcon } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { ApiError } from '../lib/http'
import { errorMessage, translateMessage } from '../lib/i18n'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './context'
import { Field } from './Field'
import { inputClass, linkClass, primaryButtonClass } from './styles'

const MIN_PASSWORD_LENGTH = 8
const INVALID_TOKEN_KEY = 'auth.resetTokenInvalid'

type Status = 'checking' | 'ready' | 'invalid'

/**
 * /reset-password?token=...: mở từ link trong mail đặt lại mật khẩu.
 * Token chỉ dùng được 1 lần, sống 15 phút; gửi link mới thì link cũ hỏng (xem docs/api-contract.md → Bước 10).
 */
export function ResetPasswordPage() {
  const { t } = useTranslation('auth')
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  // Giữ token trong state rồi xoá khỏi URL: không nằm lại trong lịch sử trình duyệt, không lộ khi chia sẻ màn hình
  const [token] = useState(() => searchParams.get('token'))
  const [status, setStatus] = useState<Status>(token ? 'checking' : 'invalid')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  // StrictMode chạy effect 2 lần lúc dev: chỉ cần kiểm tra token một lần
  const started = useRef(false)

  useEffect(() => {
    if (started.current || !token) return
    started.current = true
    setSearchParams({}, { replace: true })
    // Chỉ hỏi link còn dùng được không (không dùng mất token) để báo sớm, khỏi gõ mật khẩu rồi mới biết link hỏng
    api
      .validateResetToken(token)
      .then(() => setStatus('ready'))
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.message === INVALID_TOKEN_KEY) {
          setStatus('invalid')
          return
        }
        // Lỗi mạng/server: vẫn cho nhập, lúc gửi sẽ biết
        toast.error(errorMessage(err))
        setStatus('ready')
      })
  }, [token, setSearchParams])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!token) return
    if (password !== confirm) {
      toast.error(t('passwordMismatch'))
      return
    }
    setSubmitting(true)
    try {
      const res = await api.resetPassword(token, password)
      toast.success(translateMessage(res.message ?? 'auth.passwordResetSuccess'))
      // Backend đã đăng xuất mọi thiết bị; trình duyệt này cũng bỏ phiên cũ để đăng nhập lại bằng mật khẩu mới
      if (user) logout()
      navigate('/login', { replace: true })
    } catch (err) {
      toast.error(errorMessage(err))
      if (err instanceof ApiError && err.message === INVALID_TOKEN_KEY) setStatus('invalid')
    } finally {
      setSubmitting(false)
    }
  }

  if (status === 'checking') {
    return (
      <AuthLayout>
        <div className="flex items-center justify-center gap-2 py-6 text-sm text-slate-500 dark:text-slate-400">
          <LoaderCircleIcon size={18} className="animate-spin" />
          {t('checkingResetLink')}
        </div>
      </AuthLayout>
    )
  }

  if (status === 'invalid') {
    return (
      <AuthLayout>
        <div className="flex flex-col items-center text-center">
          <span className="mb-4 flex size-12 items-center justify-center rounded-full bg-rose-50 text-rose-500 dark:bg-rose-500/15 dark:text-rose-300">
            <LinkIcon size={22} />
          </span>
          <h2 className="mb-2 text-lg font-semibold">{t('resetLinkInvalidTitle')}</h2>
          <p className="text-sm text-slate-600 dark:text-slate-300">{t('resetLinkInvalidBody')}</p>
          <Link to="/forgot-password" className={`mt-5 block text-center ${primaryButtonClass}`}>
            {t('requestNewLink')}
          </Link>
          <Link to="/login" className={`mt-4 text-sm ${linkClass}`}>
            {t('backToLogin')}
          </Link>
        </div>
      </AuthLayout>
    )
  }

  const toggle = (
    <button
      type="button"
      onClick={() => setShowPassword((s) => !s)}
      className="rounded-md p-1 text-slate-400 transition hover:text-slate-700 dark:hover:text-slate-200"
      aria-label={t(showPassword ? 'hidePassword' : 'showPassword')}
    >
      {showPassword ? <EyeIcon size={16} /> : <EyeOffIcon size={16} />}
    </button>
  )

  return (
    <AuthLayout>
      <h2 className="mb-2 text-lg font-semibold">{t('resetPasswordTitle')}</h2>
      <p className="mb-5 text-sm text-slate-500 dark:text-slate-400">{t('resetPasswordHint')}</p>

      <form onSubmit={onSubmit} className="space-y-3">
        <Field icon={<LockIcon size={16} />} trailing={toggle}>
          <input
            className={`${inputClass} pr-10`}
            type={showPassword ? 'text' : 'password'}
            placeholder={t('newPasswordPlaceholder')}
            aria-label={t('newPassword')}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={MIN_PASSWORD_LENGTH}
            autoComplete="new-password"
            autoFocus
          />
        </Field>
        <Field icon={<LockIcon size={16} />}>
          <input
            className={inputClass}
            type={showPassword ? 'text' : 'password'}
            placeholder={t('confirmPassword')}
            aria-label={t('confirmPassword')}
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            minLength={MIN_PASSWORD_LENGTH}
            autoComplete="new-password"
          />
        </Field>
        <button type="submit" disabled={submitting} className={`mt-1 ${primaryButtonClass}`}>
          {t(submitting ? 'submitting' : 'resetPassword')}
        </button>
      </form>

      <p className="mt-6 text-center text-sm">
        <Link to="/login" className={linkClass}>
          {t('backToLogin')}
        </Link>
      </p>
    </AuthLayout>
  )
}
