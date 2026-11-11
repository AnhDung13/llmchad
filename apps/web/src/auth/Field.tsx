import type { ReactNode } from 'react'

/** Ô nhập có icon bên trái, tuỳ chọn nút bên phải (vd ẩn/hiện mật khẩu) */
export function Field({ icon, trailing, children }: { icon: ReactNode; trailing?: ReactNode; children: ReactNode }) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-slate-400">{icon}</span>
      {children}
      {trailing && <span className="absolute top-1/2 right-2 -translate-y-1/2">{trailing}</span>}
    </div>
  )
}
