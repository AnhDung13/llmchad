import { code } from '@streamdown/code'
import { memo, useMemo, type ComponentProps, type ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import { Streamdown, defaultComponents, type Components, type ExtraProps, type StreamdownTranslations } from 'streamdown'
import { CITE_TITLE_PREFIX, linkCitations } from '../lib/citations'
import type { Source } from '../lib/types'
import { Favicon } from './Favicon'

// Streamdown thay cho react-markdown: tự đóng tạm markdown dở dang khi đang stream (không lộ `**`,
// khối ``` chưa đóng không nuốt phần sau), chỉ render lại block cuối, tô màu code bằng Shiki.
const plugins = { code }
// Bảng không có nút nào; khối code chỉ giữ nút copy, bỏ nút tải file
const controls = { table: false, code: { copy: true, download: false } }

type Props = {
  text: string
  /** Đang nhận chữ: hiện chấm tròn ở cuối và hiệu ứng chữ hiện dần */
  streaming?: boolean
  /** Nguồn web search: [n] trong câu trả lời thành pill trỏ tới sources[n - 1] */
  sources?: Source[]
}

export const Markdown = memo(function Markdown({ text, streaming = false, sources }: Props) {
  const { t } = useTranslation('thread')
  // Chữ trên nút copy, hộp thoại mở link... của Streamdown: locales/<lang>/thread.json → markdown
  const translations = useMemo(() => t('markdown', { returnObjects: true }) as Partial<StreamdownTranslations>, [t])
  const markdown = useMemo(() => linkCitations(text, sources), [text, sources])
  const components = useMemo<Components>(() => ({ a: (props) => <Link {...props} sources={sources} /> }), [sources])

  return (
    <Streamdown
      plugins={plugins}
      isAnimating={streaming}
      animated
      caret="circle"
      translations={translations}
      controls={controls}
      components={components}
      // 0 = không giới hạn chiều cao (mặc định code 400px, bảng 300px rồi cuộn bên trong)
      codeBlockMaxHeight={0}
      tableMaxHeight={0}
      className="text-base leading-7"
    >
      {markdown}
    </Streamdown>
  )
})

type LinkProps = ComponentProps<'a'> & ExtraProps

const DefaultLink = (defaultComponents.a ?? 'a') as ComponentType<LinkProps>

/**
 * Link do linkCitations tạo (title "cite:n") → pill tên miền, bấm là mở trang luôn.
 * Link thường giữ component mặc định của Streamdown (hỏi lại trước khi mở trang ngoài).
 */
function Link({ sources, ...props }: LinkProps & { sources?: Source[] }) {
  const { title, href, children, node: _node, ...rest } = props
  if (!title?.startsWith(CITE_TITLE_PREFIX)) return <DefaultLink {...props} />
  const source = sources?.[Number(title.slice(CITE_TITLE_PREFIX.length)) - 1]
  return (
    <a
      {...rest}
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      title={source?.title || href}
      className="ml-0.5 inline-flex max-w-[14rem] -translate-y-px items-center gap-1 rounded-full bg-neutral-200/70 py-px pr-2 pl-1 align-middle text-xs leading-5 font-medium text-neutral-600 no-underline transition hover:bg-violet-100 hover:text-violet-800 dark:bg-night-700 dark:text-neutral-300 dark:hover:bg-violet-500/20 dark:hover:text-violet-100"
    >
      {href && <Favicon url={href} size={14} />}
      <span className="truncate">{children}</span>
    </a>
  )
}
