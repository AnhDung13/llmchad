import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { BrandName } from '../ui/Logo'

const YEAR = new Date().getFullYear()

/** Khung chung của các trang đăng nhập, đăng ký, xác minh email, OAuth callback */
export function AuthLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation('auth')
  return (
    <div className="relative flex min-h-full flex-col overflow-hidden bg-gradient-to-b from-white via-indigo-50/40 to-indigo-100/70 dark:from-night-950 dark:via-night-950 dark:to-night-900">
      <Waves />

      <div className="relative flex flex-1 flex-col items-center justify-center px-4 py-12">
        <div className="mb-8 flex flex-col items-center gap-4 text-center">
          <h1>
            <BrandName className="text-4xl font-bold" />
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">{t('tagline')}</p>
        </div>

        <div className="w-full max-w-sm rounded-2xl border border-slate-200/80 bg-white/90 p-6 shadow-xl shadow-indigo-900/5 backdrop-blur dark:border-night-700 dark:bg-night-850/80 dark:shadow-black/30">
          {children}
        </div>
      </div>

      <p className="relative pb-6 text-center text-xs text-slate-400 dark:text-slate-500">
        {t('copyright', { year: YEAR })}
      </p>
    </div>
  )
}

function Waves() {
  return (
    <svg
      className="pointer-events-none absolute inset-x-0 bottom-0 h-[45%] w-full text-indigo-300 dark:text-indigo-500"
      viewBox="0 0 1440 400"
      preserveAspectRatio="none"
      aria-hidden
    >
      <path fill="currentColor" fillOpacity="0.18" d="M0 220C240 140 480 120 720 180s480 140 720 40v180H0Z" />
      <path fill="currentColor" fillOpacity="0.14" d="M0 280c260-90 520-60 760 0s460 60 680-40v160H0Z" />
      <path fill="currentColor" fillOpacity="0.1" d="M0 330c300-50 560-20 820 20s420 20 620-30v80H0Z" />
    </svg>
  )
}
