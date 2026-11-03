# apps/web — giao diện chat (React + Vite + TypeScript)

```
cp .env.example .env.local   # lần đầu
npm run dev                  # từ thư mục gốc repo: chạy cả Spring Boot (cổng 8080) và web (cổng 5173)
```

| Thư mục / file | Nội dung |
|---|---|
| `.env.local` | `VITE_API_URL` = địa chỉ backend. Vite proxy `/api/*` tới đây |
| `src/lib/api.ts` | Các endpoint của backend, khớp `docs/api-contract.md` |
| `src/lib/http.ts` | Gắn `Authorization: Bearer`, đọc lỗi Problem Details, tự gọi `/api/auth/refresh` khi access token hết hạn |
| `src/lib/session.ts` | Lưu access token và refresh token |
| `src/lib/sse.ts` | Đọc SSE từ `fetch` (EventSource không gửi được header Authorization) |
| `src/auth/` | Đăng nhập, đăng ký, đăng xuất |
| `src/chat/` | Sidebar, danh sách tin nhắn (Markdown), ô soạn tin, nút Dừng |
