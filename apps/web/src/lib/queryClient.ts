// TanStack Query: cache dữ liệu từ backend + báo lỗi chung bằng toast.
// Query/mutation nào lỗi cũng hiện toast; không cần tự try/catch ở từng component.

import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ApiError } from './errors'
import { errorMessage } from './i18n'

const isAbort = (err: unknown) => err instanceof DOMException && err.name === 'AbortError'

function notify(err: unknown) {
  if (isAbort(err)) return
  const message = errorMessage(err)
  // id = nội dung → nhiều request cùng lỗi (vd phiên hết hạn) chỉ hiện một toast
  toast.error(message, { id: message })
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: notify }),
  mutationCache: new MutationCache({ onError: notify }),
  defaultOptions: {
    queries: {
      // Lỗi 4xx (sai dữ liệu, hết phiên, không tìm thấy) thử lại cũng vô ích
      retry: (failureCount, err) =>
        failureCount < 1 && !(err instanceof ApiError && err.status >= 400 && err.status < 500),
      refetchOnWindowFocus: false,
    },
    mutations: { retry: false },
  },
})

/** Query key dùng chung, để invalidate/setQueryData gõ đúng một chỗ */
export const keys = {
  me: ['me'] as const,
  conversations: ['conversations'] as const,
  searchProviders: ['searchProviders'] as const,
  // Không đặt dưới ['conversations', ...]: invalidate danh sách hội thoại không được đụng tới tin nhắn đang stream
  messages: (conversationId: string) => ['messages', conversationId] as const,
}
