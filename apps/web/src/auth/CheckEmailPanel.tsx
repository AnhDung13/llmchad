import { MailCheckIcon } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { ApiError } from '../lib/http'
import { errorMessage, translateMessage } from '../lib/i18n'
import type { VerificationCodeResponse } from '../lib/types'
import { useAuth } from './context'

/** Khớp với backend (xem docs/api-contract.md → Bước 8). Thời hạn mã và thời gian chờ gửi lại do backend trả về. */
const CODE_LENGTH = 6

/** Bản đăng ký đã mất ở backend: phải đăng ký lại */
const EXPIRED_KEYS = new Set(['auth.verificationExpired', 'auth.verifyAttemptsExceeded'])

type Props = {
  email: string
  /** Mật khẩu vừa nhập ở form đăng ký, gửi kèm mã để backend chắc bản đăng ký không bị người khác ghi đè */
  password: string
  /** Thời hạn của mã vừa gửi lúc đăng ký */
  initialVerification: VerificationCodeResponse
  /** Nhập đúng mã: đã đăng nhập xong */
  onVerified(): void
  /** Bản đăng ký hết hạn (15 phút) hoặc sai mã quá nhiều lần: quay lại form đăng ký */
  onExpired(): void
}

/** "Kiểm tra hộp thư": nhập mã xác minh vừa gửi tới email sau khi đăng ký */
/** Mốc thời gian (ms) hết hạn mã và được gửi lại, tính từ lúc nhận response */
function deadlines(v: VerificationCodeResponse) {
  const now = Date.now()
  return { codeExpiresAt: now + v.expiresIn * 1000, resendAt: now + v.resendAfter * 1000 }
}

/** 299 → "04:59" */
function formatSeconds(total: number) {
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function CheckEmailPanel({ email, password, initialVerification, onVerified, onExpired }: Props) {
  const { t } = useTranslation('auth')
  const { verifyEmail } = useAuth()
  const [code, setCode] = useState('')
  const [verifying, setVerifying] = useState(false)
  const [sending, setSending] = useState(false)
  const [{ codeExpiresAt, resendAt }, setDeadlines] = useState(() => deadlines(initialVerification))
  // Lưu mốc thời gian thay vì đếm lùi từng giây: tab bị ẩn (trình duyệt làm chậm timer) vẫn hiện đúng thời gian
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const codeSecondsLeft = Math.max(0, Math.ceil((codeExpiresAt - now) / 1000))
  const resendSecondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000))
  const codeExpired = codeSecondsLeft === 0

  function handleError(err: unknown) {
    toast.error(errorMessage(err))
    if (!(err instanceof ApiError)) return
    if (EXPIRED_KEYS.has(err.message)) onExpired()
    // Đồng hồ máy người dùng lệch với server: tin server, hiện là mã đã hết hạn
    if (err.message === 'auth.verifyCodeExpired') setDeadlines((d) => ({ ...d, codeExpiresAt: Date.now() }))
    // Server vẫn đang trong thời gian chờ (vd đã gửi lại ở tab khác): khoá nút gửi lại thêm một lượt chờ
    if (err.message === 'auth.tooManyRequests') {
      setDeadlines((d) => ({ ...d, resendAt: Date.now() + initialVerification.resendAfter * 1000 }))
      setNow(Date.now())
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setVerifying(true)
    try {
      const key = await verifyEmail(email, password, code)
      toast.success(key ? translateMessage(key) : t('loginSuccess'))
      onVerified()
    } catch (err) {
      handleError(err)
      setCode('')
    } finally {
      setVerifying(false)
    }
  }

  async function resend() {
    setSending(true)
    try {
      const res = await api.resendVerification(email)
      toast.success(translateMessage(res.message ?? 'auth.verificationSent'))
      setCode('')
      setDeadlines(deadlines(res.data))
      setNow(Date.now())
    } catch (err) {
      handleError(err)
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="flex flex-col items-center text-center">
      <span className="mb-4 flex size-12 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 dark:bg-indigo-500/15 dark:text-indigo-300">
        <MailCheckIcon size={22} />
      </span>
      <h2 className="mb-2 text-lg font-semibold">{t('checkEmailTitle')}</h2>
      <p className="text-sm text-slate-600 dark:text-slate-300">
        {/* Email in đậm: <b> trong locales/<lang>/auth.json */}
        <Trans
          t={t}
          i18nKey="checkEmailBody"
          values={{ email }}
          components={{ b: <strong className="font-medium break-all text-slate-900 dark:text-white" /> }}
        />
      </p>
      <p className="mt-2 text-xs text-slate-400">{t('checkEmailHint')}</p>

      <form onSubmit={onSubmit} className="mt-5 w-full space-y-3">
        <input
          // Mã phân biệt hoa thường: không tự viết hoa, không sửa chính tả
          className="w-full rounded-lg border border-slate-200 bg-white py-2.5 text-center font-mono text-xl tracking-[0.5em] outline-none transition placeholder:font-sans placeholder:text-sm placeholder:tracking-normal placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 dark:border-night-700 dark:bg-night-900 dark:placeholder:text-slate-500"
          placeholder={t('codePlaceholder')}
          aria-label={t('codePlaceholder')}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/[^0-9A-Za-z]/g, '').slice(0, CODE_LENGTH))}
          required
          minLength={CODE_LENGTH}
          maxLength={CODE_LENGTH}
          autoComplete="one-time-code"
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          autoFocus
        />
        <p className="text-xs text-slate-400">{t('codeHint')}</p>
        <p
          className={`text-sm font-medium tabular-nums ${codeExpired ? 'text-rose-500' : 'text-slate-600 dark:text-slate-300'}`}
          aria-live="polite"
        >
          {codeExpired ? t('codeExpired') : t('codeExpiresIn', { time: formatSeconds(codeSecondsLeft) })}
        </p>
        <button
          type="submit"
          disabled={verifying || codeExpired || code.length !== CODE_LENGTH}
          className="w-full rounded-lg bg-gradient-to-r from-indigo-500 to-violet-500 px-4 py-2.5 text-sm font-medium text-white shadow-md shadow-indigo-500/25 transition hover:from-indigo-600 hover:to-violet-600 disabled:opacity-60"
        >
          {t(verifying ? 'submitting' : 'verify')}
        </button>
      </form>

      {/* Mã hết hạn (3 phút) chỉ cần gửi lại; bản đăng ký hết hạn (15 phút) thì backend trả lỗi → onExpired */}
      <button
        type="button"
        onClick={resend}
        disabled={sending || resendSecondsLeft > 0}
        className="mt-4 text-sm font-medium text-indigo-600 hover:underline disabled:cursor-not-allowed disabled:text-slate-400 disabled:no-underline dark:text-indigo-400"
      >
        {sending ? t('submitting') : resendSecondsLeft > 0 ? t('resendIn', { seconds: resendSecondsLeft }) : t('resend')}
      </button>
    </div>
  )
}
