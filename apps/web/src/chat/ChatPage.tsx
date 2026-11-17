import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarIcon, CodeXmlIcon, EllipsisIcon, FileTextIcon, LightbulbIcon, MenuIcon, MoonIcon, PanelLeftIcon, PencilIcon, SquarePenIcon, SunIcon, TrashIcon } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useMatch, useNavigate } from 'react-router'
import { api } from '../lib/api'
import { errorMessage } from '../lib/i18n'
import { keys } from '../lib/queryClient'
import { useTheme } from '../lib/theme'
import type { Conversation, Message, SearchProvider } from '../lib/types'
import { BrandName } from '../ui/Logo'
import { MarqueeText } from '../ui/MarqueeText'
import { Menu, iconButton } from '../ui/Menu'
import { Composer } from './Composer'
import { DeleteDialog, RenameDialog, TITLE_MAX } from './ConversationDialogs'
import { MessageList, type UiMessage } from './MessageList'
import { Sidebar } from './Sidebar'

// Chữ của từng gợi ý nằm trong locales/<lang>/chat.json: suggestions.<id>.title / prompt
const SUGGESTIONS: { id: string; icon: ReactNode }[] = [
  { id: 'explain', icon: <LightbulbIcon size={16} className="text-amber-500" /> },
  { id: 'code', icon: <CodeXmlIcon size={16} className="text-sky-500" /> },
  { id: 'plan', icon: <CalendarIcon size={16} className="text-emerald-500" /> },
  { id: 'summarize', icon: <FileTextIcon size={16} className="text-orange-500" /> },
]

type DialogTarget = Pick<Conversation, 'id' | 'title'>

const toUi = (m: Message): UiMessage => ({
  key: String(m.id),
  role: m.role,
  content: m.content,
  reasoning: m.reasoning ?? undefined,
  sources: m.sources ?? undefined,
})
const isAbort = (err: unknown) => err instanceof DOMException && err.name === 'AbortError'

// Nhớ provider web search đã chọn giữa các lần mở trang. localStorage có thể bị chặn (chế độ riêng tư) → bỏ qua
const SEARCH_PROVIDER_KEY = 'llmchad.searchProvider'
function readSearchProvider(): SearchProvider | null {
  try {
    return localStorage.getItem(SEARCH_PROVIDER_KEY) as SearchProvider | null
  } catch {
    return null
  }
}
function saveSearchProvider(provider: SearchProvider | null) {
  try {
    if (provider) localStorage.setItem(SEARCH_PROVIDER_KEY, provider)
    else localStorage.removeItem(SEARCH_PROVIDER_KEY)
  } catch {
    // bỏ qua
  }
}
/**
 * Tin nhắn của một cuộc trò chuyện. Cache này cũng là nơi giữ tin nhắn đang stream,
 * nên staleTime: Infinity để không bao giờ có lần tải lại nào đè lên.
 */
const messagesQuery = (conversationId: string) =>
  queryOptions({
    queryKey: keys.messages(conversationId),
    queryFn: async ({ signal }) => (await api.listMessages(conversationId, signal)).map(toUi),
    staleTime: Infinity,
  })

export function ChatPage() {
  const { t } = useTranslation('chat')
  const match = useMatch('/c/:id')
  const conversationId = match?.params.id ?? null
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { theme, toggle: toggleTheme } = useTheme()

  const conversationsQuery = useQuery({
    queryKey: keys.conversations,
    queryFn: ({ signal }) => api.listConversations(signal),
  })
  const conversations = conversationsQuery.data ?? []

  const messagesResult = useQuery({ ...messagesQuery(conversationId ?? ''), enabled: conversationId !== null })
  const messages = conversationId === null ? [] : (messagesResult.data ?? [])
  const messagesLoading = conversationId !== null && messagesResult.isPending

  // Lỗi đã hiện toast qua QueryCache/MutationCache (lib/queryClient.ts), ở đây không cần try/catch
  const createConversation = useMutation({ mutationFn: (title: string) => api.createConversation(title) })
  const deleteConversation = useMutation({
    mutationFn: (id: string) => api.deleteConversation(id),
    onSuccess: (_data, id) => {
      queryClient.setQueryData<Conversation[]>(keys.conversations, (prev) => prev?.filter((c) => c.id !== id))
      queryClient.removeQueries({ queryKey: keys.messages(id) })
    },
  })

  const renameConversation = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => api.renameConversation(id, title),
    onSuccess: (conv) => {
      queryClient.setQueryData<Conversation[]>(keys.conversations, (prev) => prev?.map((c) => (c.id === conv.id ? conv : c)))
    },
  })

  // Conversation đang mở hộp thoại đổi tên / xoá (null = đóng)
  const [renaming, setRenaming] = useState<DialogTarget | null>(null)
  const [deleting, setDeleting] = useState<DialogTarget | null>(null)

  const [streaming, setStreaming] = useState(false)
  // Provider nào backend chưa có key thì không hiện; tải lỗi thì coi như không có provider nào (ẩn nút)
  const searchProvidersQuery = useQuery({
    queryKey: keys.searchProviders,
    queryFn: ({ signal }) => api.listSearchProviders(signal),
    staleTime: Infinity,
  })
  const searchProviders = searchProvidersQuery.data ?? []
  const [pickedProvider, setPickedProvider] = useState(readSearchProvider)
  useEffect(() => saveSearchProvider(pickedProvider), [pickedProvider])
  // Đã chọn trước đó nhưng giờ backend không còn provider đó → coi như tắt
  const searchProvider = pickedProvider && searchProviders.includes(pickedProvider) ? pickedProvider : null
  const [sidebarOpen, setSidebarOpen] = useState(false) // ngăn kéo trên mobile
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false) // thu gọn trên desktop

  const abortRef = useRef<AbortController | null>(null)
  const streamingConvRef = useRef<string | null>(null)
  const seqRef = useRef(0)

  // Huỷ stream đang chạy khi rời trang (ví dụ đăng xuất)
  useEffect(() => () => abortRef.current?.abort(), [])

  // Người dùng chuyển sang cuộc trò chuyện khác → huỷ stream đang chạy ở cuộc cũ.
  // Chỉ phụ thuộc URL: React Router đổi URL trong một transition.
  useEffect(() => {
    if (streamingConvRef.current !== null && streamingConvRef.current !== conversationId) abortRef.current?.abort()
  }, [conversationId])

  async function send(content: string) {
    const seq = ++seqRef.current
    const aiKey = `pending-ai-${seq}`
    const pending: UiMessage[] = [
      { key: `pending-user-${seq}`, role: 'user', content },
      { key: aiKey, role: 'assistant', content: '', streaming: true },
    ]

    let convId: string
    if (conversationId === null) {
      let conv: Conversation
      try {
        // Tiêu đề theo ngôn ngữ giao diện đang chọn
        conv = await createConversation.mutateAsync(t('newChat', { ns: 'sidebar' }))
      } catch {
        return // toast do MutationCache hiện
      }
      convId = conv.id
      queryClient.setQueryData<Conversation[]>(keys.conversations, (prev) => [conv, ...(prev ?? [])])
      // Gieo sẵn tin nhắn vào cache → trang /c/:id mới không phải tải từ backend
      queryClient.setQueryData(keys.messages(conv.id), pending)
      navigate(`/c/${conv.id}`)
    } else {
      convId = conversationId
      // Tin nhắn cũ đang tải dở thì đợi tải xong rồi mới nối thêm, để lần tải đó không đè mất tin mới
      await queryClient.ensureQueryData(messagesQuery(convId)).catch(() => {})
      queryClient.setQueryData<UiMessage[]>(keys.messages(convId), (prev) => [...(prev ?? []), ...pending])
    }

    const updateAi = (fn: (m: UiMessage) => UiMessage) =>
      queryClient.setQueryData<UiMessage[]>(keys.messages(convId), (prev) => prev?.map((m) => (m.key === aiKey ? fn(m) : m)))

    const controller = new AbortController()
    abortRef.current = controller
    streamingConvRef.current = convId
    setStreaming(true)
    try {
      for await (const ev of api.sendMessage(convId, content, searchProvider, controller.signal)) {
        if (ev.type === 'reasoning') updateAi((m) => ({ ...m, reasoning: (m.reasoning ?? '') + ev.text }))
        else if (ev.type === 'delta') updateAi((m) => ({ ...m, searching: undefined, content: m.content + ev.text }))
        else if (ev.type === 'search') updateAi((m) => ({ ...m, searching: ev.query }))
        else if (ev.type === 'title')
          queryClient.setQueryData<Conversation[]>(keys.conversations, (prev) =>
            prev?.map((c) => (c.id === convId ? { ...c, title: ev.title } : c)),
          )
        else if (ev.type === 'sources') updateAi((m) => ({ ...m, searching: undefined, sources: [...(m.sources ?? []), ...ev.sources] }))
        else updateAi(() => toUi(ev.message))
      }
    } catch (err) {
      if (isAbort(err)) updateAi((m) => ({ ...m, streaming: false, stopped: true }))
      else updateAi((m) => ({ ...m, streaming: false, error: errorMessage(err) }))
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null
        streamingConvRef.current = null
      }
      setStreaming(false)
      // Tiêu đề và thứ tự có thể đã đổi
      void queryClient.invalidateQueries({ queryKey: keys.conversations })
    }
  }

  function remove(id: string) {
    deleteConversation.mutate(id, {
      onSuccess: () => {
        if (id === conversationId) navigate('/')
      },
    })
  }

  const current = conversations.find((c) => c.id === conversationId)
  // Trang chào (gợi ý) cho cả trang / lẫn conversation chưa có tin nhắn nào
  const empty = !messagesLoading && messages.length === 0

  const sidebarProps = {
    conversations,
    loading: conversationsQuery.isPending,
    error: conversationsQuery.error ? errorMessage(conversationsQuery.error) : null,
    onRename: setRenaming,
    onDelete: setDeleting,
  }

  return (
    <div className="flex h-full">
      {/* Sidebar: cố định trên desktop (có thể thu gọn), trượt ra trên mobile */}
      {!sidebarCollapsed && (
        <div className="hidden md:block">
          <Sidebar
            {...sidebarProps}
            onNewChat={() => navigate('/')}
            onNavigate={() => {}}
            onCollapse={() => setSidebarCollapsed(true)}
          />
        </div>
      )}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={() => setSidebarOpen(false)} />
          <div className="relative h-full w-[260px] shadow-xl">
            <Sidebar
              {...sidebarProps}
              onNewChat={() => {
                navigate('/')
                setSidebarOpen(false)
              }}
              onNavigate={() => setSidebarOpen(false)}
              onCollapse={() => setSidebarOpen(false)}
            />
          </div>
        </div>
      )}

      {/* Nền ánh tím đậm dần xuống dưới (sáng: trắng → violet-50, tối: night-900 → tím than), cùng tông sidebar; chữ vẫn dễ đọc */}
      <main className="flex min-w-0 flex-1 flex-col bg-gradient-to-b from-[#fdfcff] to-violet-50 dark:from-night-900 dark:to-[#211b31]">
        <header className="flex h-[52px] shrink-0 items-center gap-1 px-2">
          <button type="button" className={`${iconButton} md:hidden`} onClick={() => setSidebarOpen(true)} aria-label={t('openConversationList')}>
            <MenuIcon size={18} />
          </button>
          {sidebarCollapsed && (
            <>
              <button
                type="button"
                className={`${iconButton} hidden md:flex`}
                onClick={() => setSidebarCollapsed(false)}
                aria-label={t('openSidebar')}
                title={t('openSidebar')}
              >
                <PanelLeftIcon size={18} />
              </button>
              <button
                type="button"
                className={`${iconButton} hidden md:flex`}
                onClick={() => navigate('/')}
                aria-label={t('newChat', { ns: 'sidebar' })}
                title={t('newChat', { ns: 'sidebar' })}
              >
                <SquarePenIcon size={18} />
              </button>
            </>
          )}
          {current ? (
            // key: đổi conversation thì thoát chế độ sửa
            <TitleEditor key={current.id} title={current.title} onRename={(title) => renameConversation.mutate({ id: current.id, title })} />
          ) : conversationId !== null ? (
            // Conversation không có trong trang danh sách đã tải → chưa biết tên, không cho sửa
            <h1 className="min-w-0 truncate px-2 text-base">{t('conversationFallback')}</h1>
          ) : (
            // Tên app đã nằm ở đầu sidebar; chỉ hiện ở header khi không thấy sidebar (mobile, hoặc đã thu gọn)
            <h1 className={`px-2 ${sidebarCollapsed ? '' : 'md:hidden'}`}>
              <BrandName />
            </h1>
          )}

          <div className="ml-auto flex items-center gap-1">
            <button
              type="button"
              onClick={toggleTheme}
              className={iconButton}
              aria-label={theme === 'dark' ? t('switchToLight') : t('switchToDark')}
              title={theme === 'dark' ? t('lightTheme') : t('darkTheme')}
            >
              {theme === 'dark' ? <SunIcon size={18} /> : <MoonIcon size={18} />}
            </button>
            {conversationId !== null && (
              <Menu
                label={t('conversationOptions')}
                trigger={<EllipsisIcon size={18} />}
                triggerClassName={iconButton}
                items={[
                  // Conversation không có trong trang danh sách đã tải thì chưa biết tên → chỉ cho xoá
                  ...(current ? [{ label: t('renameConversation'), icon: <PencilIcon size={15} />, onSelect: () => setRenaming(current) }] : []),
                  {
                    label: t('deleteConversation'),
                    icon: <TrashIcon size={15} />,
                    danger: true,
                    onSelect: () => setDeleting({ id: conversationId, title: current?.title ?? t('thisConversation') }),
                  },
                ]}
              />
            )}
          </div>
        </header>

        {empty ? (
          // Màn trống như ChatGPT: câu hỏi ở giữa, ô nhập ngay bên dưới, gợi ý dạng nút nhỏ
          <div className="flex flex-1 flex-col items-center justify-center overflow-y-auto pb-[10vh]">
            <h2 className="mb-8 px-4 text-center text-[28px] font-normal tracking-tight">{t('emptyHeading')}</h2>
            <Composer
              streaming={streaming}
              searchProvider={searchProvider}
              searchProviders={searchProviders}
              onSearchProviderChange={setPickedProvider}
              onSend={(text) => void send(text)}
              onStop={() => abortRef.current?.abort()}
              autoFocusKey={conversationId ?? 'new'}
              showHint={false}
            />
            <div className="mt-3 flex max-w-3xl flex-wrap justify-center gap-2 px-4">
              {SUGGESTIONS.map((s) => {
                const prompt = t(`suggestions.${s.id}.prompt`)
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => void send(prompt)}
                    title={prompt}
                    className="flex items-center gap-2 rounded-full border border-neutral-200 px-3.5 py-2 text-sm text-neutral-600 transition hover:border-violet-200 hover:bg-violet-50 hover:text-violet-900 dark:border-night-700 dark:text-neutral-300 dark:hover:border-violet-500/40 dark:hover:bg-violet-500/10 dark:hover:text-violet-100"
                  >
                    {s.icon}
                    {t(`suggestions.${s.id}.title`)}
                  </button>
                )
              })}
            </div>
          </div>
        ) : (
          <>
            {messagesLoading ? (
              <div className="flex flex-1 items-center justify-center text-sm text-neutral-500">{t('loadingMessages')}</div>
            ) : (
              // key: đổi conversation thì mount lại để nhảy xuống tin mới nhất
              <MessageList key={conversationId} messages={messages} />
            )}
            <Composer
              streaming={streaming}
              searchProvider={searchProvider}
              searchProviders={searchProviders}
              onSearchProviderChange={setPickedProvider}
              onSend={(text) => void send(text)}
              onStop={() => abortRef.current?.abort()}
              autoFocusKey={conversationId ?? 'new'}
            />
          </>
        )}
      </main>

      <RenameDialog target={renaming} onClose={() => setRenaming(null)} onRename={(id, title) => renameConversation.mutate({ id, title })} />
      <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onDelete={remove} />
    </div>
  )
}

/** Tên conversation trên header: bấm vào thành ô nhập. Enter / bấm ra ngoài = lưu, Esc = huỷ */
function TitleEditor({ title, onRename }: { title: string; onRename(title: string): void }) {
  const { t } = useTranslation('chat')
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(title)
  // Esc: đánh dấu huỷ để lần blur (nếu có) khi ô nhập biến mất không lưu
  const cancelledRef = useRef(false)

  function commit() {
    if (cancelledRef.current) return
    const trimmed = value.trim()
    if (trimmed && trimmed !== title) onRename(trimmed)
    setEditing(false)
  }

  if (!editing) {
    return (
      <h1 className="min-w-0">
        <button
          type="button"
          onClick={() => {
            setValue(title)
            cancelledRef.current = false
            setEditing(true)
          }}
          className="marquee-host block max-w-full rounded-lg px-2 py-1 text-left text-base transition hover:bg-violet-100/60 dark:hover:bg-violet-500/10"
          title={t('clickToRename')}
        >
          <MarqueeText text={title} />
        </button>
      </h1>
    )
  }

  return (
    <input
      value={value}
      onChange={(e) => setValue(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.nativeEvent.isComposing) return // đang gõ tiếng Việt (IME ghép chữ)
        if (e.key === 'Enter') e.currentTarget.blur()
        else if (e.key === 'Escape') {
          cancelledRef.current = true
          setEditing(false)
        }
      }}
      onFocus={(e) => e.currentTarget.select()}
      maxLength={TITLE_MAX}
      aria-label={t('renameConversation')}
      autoFocus
      className="h-8 w-full max-w-md min-w-0 rounded-lg border border-violet-300 bg-white px-2 text-base outline-none ring-4 ring-violet-100 dark:border-violet-500/40 dark:bg-night-800 dark:ring-violet-500/10"
    />
  )
}
