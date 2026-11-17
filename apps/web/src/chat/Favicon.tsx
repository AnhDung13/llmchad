import { GlobeIcon } from 'lucide-react'
import { useState } from 'react'
import { hostOf } from '../lib/citations'

type Props = { url: string; size?: number; className?: string }

/** Logo của trang nguồn, lấy từ dịch vụ favicon của Google; tải lỗi thì hiện quả địa cầu */
export function Favicon({ url, size = 14, className = '' }: Props) {
  const [failed, setFailed] = useState(false)
  if (failed) return <GlobeIcon size={size} className={`shrink-0 text-neutral-400 ${className}`} aria-hidden />
  return (
    <img
      // sz=32 rồi thu nhỏ: nét hơn trên màn hình retina
      src={`https://www.google.com/s2/favicons?domain=${encodeURIComponent(hostOf(url))}&sz=32`}
      width={size}
      height={size}
      alt=""
      loading="lazy"
      // Không gửi URL của cuộc trò chuyện cho Google
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      className={`shrink-0 rounded-sm ${className}`}
    />
  )
}
