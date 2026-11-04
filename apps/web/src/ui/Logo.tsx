import logo from '../assets/logo.png'

/**
 * Logo kèm tên app dạng chữ gradient tím, dùng ở sidebar, header và trang đăng nhập.
 * `className` đặt cỡ chữ, độ đậm; logo cao theo cỡ chữ (đơn vị em).
 * Ảnh gốc độ phân giải cao ở apps/web/brand/logo.png.
 */
export function BrandName({ className = 'text-xl font-semibold' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-[0.35em] ${className}`}>
      <img src={logo} alt="" className="h-[1.4em] w-[1.4em]" />
      <span className="bg-gradient-to-r from-violet-600 via-purple-500 to-fuchsia-500 bg-clip-text tracking-tight text-transparent dark:from-violet-400 dark:via-purple-400 dark:to-fuchsia-400">
        LLM Chad
      </span>
    </span>
  )
}

/** "Anh Dung" → "AD", "dung" → "D" */
function initials(name: string | undefined) {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0].charAt(0)
  const last = parts.length > 1 ? parts.at(-1)!.charAt(0) : ''
  return (first + last).toUpperCase()
}

export function Avatar({ name, size = 32 }: { name: string | undefined; size?: number }) {
  return (
    <div
      className="flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-blue-500 font-semibold text-white"
      style={{ width: size, height: size, fontSize: size * 0.36 }}
      aria-hidden
    >
      {initials(name)}
    </div>
  )
}
