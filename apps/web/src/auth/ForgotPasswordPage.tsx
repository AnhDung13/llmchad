import { MailCheckIcon, MailIcon } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { ApiError } from '../lib/http'
import { errorMessage, translateMessage } from '../lib/i18n'
import { AuthLayout } from './AuthLayout'
import { Field } from './Field'
import { inputClass, linkClass, primaryButtonClass } from './styles'

/** Link đã gửi: email nhận, link sống bao lâu (phút), mốc (ms) được gửi lại */
type Sent = { email: string; minutes: number; resendAt: number }

/**
 * /forgot-password: nhập email → backend gửi link /reset-password?token=... (xem docs/api-contract.md → Bước 10).
 * Backend luôn trả thành công dù email chưa đăng ký, nên màn "đã gửi" nói "nếu email đã đăng ký".
 */
export function ForgotPasswordPage() {
  const { t } = useTranslation('auth')
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState<Sent | null>(null)
  // Lưu mốc thời gian thay vì đếm lùi từng giây: tab bị ẩn vẫn hiện đúng thời gian chờ
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!sent) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [sent])

  const resendSecondsLeft = sent ? Math.max(0, Math.ceil((sent.resendAt - now) / 1000)) : 0

  async function send(target: string) {
    setSubmitting(true)
    try {
      const res = await api.forgotPassword(target)
      toast.success(translateMessage(res.message ?? 'auth.passwordResetSent'))
      setSent({
        email: target,
        minutes: Math.round(res.data.expiresIn / 60),
        resendAt: Date.now() + res.data.resendAfter * 1000,
      })
      setNow(Date.now())
    } catch (err) {
      toast.error(errorMessage(err))
      // Server vẫn đang trong thời gian chờ (vd vừa gửi ở tab khác): khoá nút gửi lại thêm một lượt chờ
      if (sent && err instanceof ApiError && err.message === 'auth.tooManyRequests') {
        setSent({ ...sent, resendAt: Date.now() + 60_000 })
        setNow(Date.now())
      }
    } finally {
      setSubmitting(false)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void send(email)
  }

  if (sent) {
    return (
      <AuthLayout>
        <div className="flex flex-col items-center text-center">
          <span className="mb-4 flex size-12 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 dark:bg-indigo-500/15 dark:text-indigo-300">
            <MailCheckIcon size={22} />
          </span>
          <h2 className="mb-2 text-lg font-semibold">{t('checkEmailTitle')}</h2>
          <p className="text-sm text-slate-600 dark:text-slate-300">
            <Trans
              t={t}
              i18nKey="resetLinkSentBody"
              values={{ email: sent.email, minutes: sent.minutes }}
              components={{ b: <strong className="font-medium break-all text-slate-900 dark:text-white" /> }}
            />
          </p>
          <p className="mt-2 text-xs text-slate-400">{t('checkEmailHint')}</p>

          <button
            type="button"
            onClick={() => void send(sent.email)}
            disabled={submitting || resendSecondsLeft > 0}
            className="mt-5 text-sm font-medium text-indigo-600 hover:underline disabled:cursor-not-allowed disabled:text-slate-400 disabled:no-underline dark:text-indigo-400"
          >
            {submitting
              ? t('submitting')
              : resendSecondsLeft > 0
                ? t('resendIn', { seconds: resendSecondsLeft })
                : t('resendResetLink')}
          </button>
          <button
            type="button"
            onClick={() => setSent(null)}
            className="mt-2 text-sm text-slate-500 hover:underline dark:text-slate-400"
          >
            {t('useAnotherEmail')}
          </button>
          <Link to="/login" className={`mt-5 text-sm ${linkClass}`}>
            {t('backToLogin')}
          </Link>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <h2 className="mb-2 text-lg font-semibold">{t('forgotPasswordTitle')}</h2>
      <p className="mb-5 text-sm text-slate-500 dark:text-slate-400">{t('forgotPasswordHint')}</p>

      <form onSubmit={onSubmit} className="space-y-3">
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
            autoFocus
          />
        </Field>
        <button type="submit" disabled={submitting} className={`mt-1 ${primaryButtonClass}`}>
          {t(submitting ? 'submitting' : 'sendResetLink')}
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
