import { ArrowUpIcon, CheckIcon, GlobeIcon, SquareIcon } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { SearchProvider } from '../lib/types'
import { Menu } from '../ui/Menu'

/** Tên thương hiệu, không dịch */
const PROVIDER_NAMES: Record<SearchProvider, string> = { tavily: 'Tavily', tinyfish: 'TinyFish' }

type Props = {
  streaming: boolean
  /** Provider web search đang chọn; null = không tìm trên web */
  searchProvider: SearchProvider | null
  /** Các provider backend đã có API key. Rỗng thì ẩn nút web search */
  searchProviders: SearchProvider[]
  onSearchProviderChange(provider: SearchProvider | null): void
  onSend(text: string): void
  onStop(): void
  autoFocusKey?: string | number
  /** Dòng nhắc nhỏ bên dưới ô nhập (ẩn ở màn trống) */
  showHint?: boolean
}

export function Composer({
  streaming,
  searchProvider,
  searchProviders,
  onSearchProviderChange,
  onSend,
  onStop,
  autoFocusKey,
  showHint = true,
}: Props) {
  const { t } = useTranslation('thread')
  const [text, setText] = useState('')
  const ref = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    ref.current?.focus()
  }, [autoFocusKey])

  // Ô nhập tự cao lên theo nội dung, tối đa 200px
  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`
  }, [text])

  function submit(e?: FormEvent) {
    e?.preventDefault()
    const value = text.trim()
    if (!value || streaming) return
    onSend(value)
    setText('')
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter gửi, Shift+Enter xuống dòng; bỏ qua khi đang gõ tiếng Việt (IME đang ghép chữ)
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  const roundButton = 'flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition'

  return (
    <form onSubmit={submit} className="mx-auto w-full max-w-3xl px-4 pb-3">
      <div
        className="flex cursor-text items-end gap-2 rounded-[28px] border border-neutral-200 bg-white py-2 pr-2 pl-5 shadow-[0_4px_16px_rgba(0,0,0,0.05)] transition focus-within:border-violet-300 focus-within:ring-4 focus-within:ring-violet-100 dark:border-transparent dark:bg-night-800 dark:shadow-none dark:focus-within:border-violet-500/40 dark:focus-within:ring-violet-500/10"
        onClick={() => ref.current?.focus()}
      >
        <textarea
          ref={ref}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          rows={1}
          placeholder={t('placeholder')}
          aria-label={t('inputLabel')}
          className="block max-h-[200px] min-w-0 flex-1 resize-none self-center bg-transparent py-1.5 text-base outline-none placeholder:text-neutral-400 dark:placeholder:text-neutral-500"
        />
        {searchProviders.length > 0 && (
          // Chặn click lan lên khung ngoài: khung đó focus ô nhập, làm menu vừa mở bị đóng
          <div onClick={(e) => e.stopPropagation()} className="shrink-0">
            <Menu
              label={searchProvider ? t('webSearchWith', { provider: PROVIDER_NAMES[searchProvider] }) : t('webSearchMenu')}
              side="top"
              align="start"
              trigger={
                <>
                  <GlobeIcon size={17} />
                  {searchProvider && <span className="hidden sm:inline">{PROVIDER_NAMES[searchProvider]}</span>}
                </>
              }
              triggerClassName={`flex h-9 items-center gap-1.5 rounded-full px-2.5 text-sm transition outline-none focus-visible:ring-2 focus-visible:ring-violet-300 ${
                searchProvider
                  ? 'bg-violet-100 text-violet-700 hover:bg-violet-200/70 dark:bg-violet-500/20 dark:text-violet-200 dark:hover:bg-violet-500/30'
                  : 'text-neutral-500 hover:bg-neutral-100 hover:text-neutral-900 dark:text-neutral-400 dark:hover:bg-night-700 dark:hover:text-neutral-100'
              }`}
              items={[
                { id: null, label: t('webSearchNone') },
                ...searchProviders.map((p) => ({ id: p, label: t('webSearchWith', { provider: PROVIDER_NAMES[p] }) })),
              ].map((o) => ({
                label: o.label,
                // Ô trống cùng cỡ để chữ các lựa chọn thẳng hàng
                icon: o.id === searchProvider ? <CheckIcon size={15} /> : <span className="w-[15px]" />,
                onSelect: () => onSearchProviderChange(o.id),
              }))}
            />
          </div>
        )}
        {streaming ? (
          <button
            type="button"
            onClick={onStop}
            className={`${roundButton} bg-brand text-white hover:brightness-110`}
            aria-label={t('stop')}
            title={t('stop')}
          >
            <SquareIcon size={13} fill="currentColor" />
          </button>
        ) : (
          <button
            type="submit"
            disabled={!text.trim()}
            className={`${roundButton} bg-brand text-white shadow-sm shadow-violet-600/30 hover:brightness-110 disabled:bg-violet-200 disabled:bg-none disabled:shadow-none dark:disabled:bg-violet-500/25 dark:disabled:text-violet-200/60`}
            aria-label={t('sendLabel')}
            title={t('send')}
          >
            <ArrowUpIcon size={18} strokeWidth={2.25} />
          </button>
        )}
      </div>
      {showHint && <p className="mt-2 text-center text-xs text-neutral-500 dark:text-neutral-400">{t('hint')}</p>}
    </form>
  )
}
