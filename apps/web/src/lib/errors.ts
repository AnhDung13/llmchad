/**
 * Lỗi khi gọi backend.
 * `message` là message key (backend: locales/server.json, frontend: locales/common.json)
 * hoặc câu chữ thô (backend chưa chuyển sang key); rỗng thì hiển thị câu mặc định theo `status`.
 * Dịch ra câu hiển thị bằng errorMessage() trong lib/i18n.ts.
 */
export class ApiError extends Error {
  readonly status: number
  /** Tham số nội suy cho key (nếu key có dùng {{...}}) */
  readonly params?: Record<string, unknown>
  constructor(status: number, message: string, params?: Record<string, unknown>) {
    super(message)
    this.status = status
    this.params = params
  }
}
