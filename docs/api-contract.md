# API contract — llmchat

Đây là "hợp đồng" giữa frontend (`apps/web`) và backend (`apps/api`).
Frontend đã code theo đúng tài liệu này. Bạn implement backend sao cho khớp,
giao diện sẽ chạy mà không phải sửa gì phía web.

- Base URL: `/api`. Khi dev, Vite proxy `/api/*` sang `http://localhost:8080`
  (xem `apps/web/vite.config.ts`), nên **không cần cấu hình CORS** lúc dev.
- Body request/response là JSON (`Content-Type: application/json`),
  trừ endpoint stream ở bước 6.
- **Mọi response JSON đều bọc trong envelope** (xem [Envelope](#envelope)). Khi tài liệu ghi
  "Response `200 OK`: một `User`" thì `User` nằm trong field `data` của envelope.
- Thời gian là chuỗi ISO-8601 UTC, ví dụ `"2026-10-19T09:30:00Z"`
  (kiểu `java.time.Instant` của Java serialize ra đúng dạng này).
- Mọi `id` (user, conversation, message) là UUID dạng chuỗi (`UUID` trong DB, `java.util.UUID` trong Java).

## Xác thực

Mọi endpoint **trừ** 4 endpoint `POST /api/auth/register`, `/login`, `/refresh`, `/logout` đều cần header:

```
Authorization: Bearer <accessToken>
```

Thiếu token, token sai hoặc token hết hạn → `401`. Khi nhận `401`, frontend gọi `POST /api/auth/refresh`
một lần rồi gửi lại request. Refresh cũng thất bại thì frontend đăng xuất và đưa người dùng về trang đăng nhập.

## Envelope

Mọi response JSON, cả thành công lẫn lỗi, có cùng một dạng (`com.llmchat.common.ApiResponse`):

```json
{
  "status": 200,
  "message": "auth.loginSuccess",
  "data": { "accessToken": "...", "user": { "id": "3f2b8c1e-...", "email": "an@example.com", "displayName": "An", "createdAt": "..." } }
}
```

| Field | Ý nghĩa |
|---|---|
| `status` | Trùng với HTTP status của response. Frontend dựa vào **HTTP status**, field này chỉ để đọc cho tiện |
| `message` | **Message key** (không phải câu chữ), xem [Message key](#message-key). `null` khi không có gì cần báo |
| `data` | Dữ liệu thật khi thành công. `null` khi lỗi hoặc khi endpoint không có dữ liệu trả về (logout, xoá) |

Ví dụ lỗi:

```json
{ "status": 409, "message": "auth.emailAlreadyExists", "data": null }
```

## Message key

Backend **không trả câu chữ viết cứng**. Field `message` chứa một key, frontend dùng i18next dịch sang ngôn ngữ người dùng
đang chọn (vi/en) rồi hiện bằng toast.

- Danh sách key: `apps/api/src/main/java/com/llmchat/common/MessageKey.java`
- Bản dịch: `apps/web/src/locales/{vi,en}/server.json` (key `auth.loginSuccess` ↔ `{ "auth": { "loginSuccess": "..." } }`)
- Thêm key mới thì thêm ở cả ba nơi. Frontend nhận một chuỗi không phải key đã biết thì hiển thị nguyên văn.

| Key | HTTP | Khi nào |
|---|---|---|
| `auth.loginSuccess` | 200 | Đăng nhập thành công |
| `auth.registerSuccess` | 201 | Đăng ký thành công |
| `auth.logoutSuccess` | 200/204 | Đăng xuất |
| `auth.emailAlreadyExists` | 409 | Email đã tồn tại |
| `auth.invalidCredentials` | 401 | Sai email hoặc mật khẩu |
| `auth.invalidRefreshToken` | 401 | Refresh token sai, hết hạn hoặc đã bị thu hồi |
| `auth.unauthorized` | 401 | Token hợp lệ nhưng không còn user tương ứng |
| `validation.failed` | 400 | Validation lỗi, không có key cụ thể hơn |
| `validation.required` | 400 | Thiếu trường bắt buộc (`@NotBlank`) |
| `validation.emailInvalid` | 400 | Sai định dạng email (`@Email`) |
| `validation.passwordTooShort` | 400 | Mật khẩu < 8 ký tự |
| `validation.displayNameTooLong` | 400 | Tên hiển thị > 50 ký tự |
| `validation.titleTooLong` | 400 | Tiêu đề conversation > 200 ký tự |
| `validation.malformedJson` | 400 | Body không phải JSON hợp lệ |
| `conversation.notFound` | 404 | Không tìm thấy, **hoặc** conversation không thuộc về user đang đăng nhập |
| `llm.failed` | 502 | Gọi LLM thất bại (kể cả event `error` của SSE) |
| `error.notFound` | 404 | Sai đường dẫn |
| `error.methodNotAllowed` | 405 | Sai HTTP method |
| `error.internal` | 500 | Lỗi không lường trước |

Validation có nhiều trường lỗi thì trả key của **trường lỗi đầu tiên** (frontend chỉ hiện một thông báo).

## Lỗi

HTTP status vẫn phải đúng mã lỗi (không trả `200` cho lỗi). Nếu response lỗi không có envelope
(ví dụ lỗi Spring chưa được bọc), frontend vẫn đọc được `detail`/`title` của Problem Details,
không có nữa thì dùng câu mặc định theo HTTP status.

## Kiểu dữ liệu

```ts
type User = {
  id: string          // UUID
  email: string
  displayName: string
  createdAt: string
}

type Conversation = {
  id: string          // UUID
  title: string
  createdAt: string
  modifiedAt: string  // cập nhật mỗi khi có tin nhắn mới
}

type Message = {
  id: string          // UUID
  role: "user" | "assistant"
  content: string     // assistant trả về Markdown
  reasoning: string | null  // phần suy luận của model; null với tin user hoặc model không trả reasoning
  createdAt: string
}
```

---

## Bước 2 – Đăng ký

### `POST /api/auth/register`

Request:
```json
{ "email": "an@example.com", "password": "matkhau123", "displayName": "An" }
```

Validation:
- `email`: bắt buộc, đúng định dạng email.
- `password`: bắt buộc, tối thiểu 8 ký tự.
- `displayName`: bắt buộc, 1–50 ký tự.

Response `201 Created`: một `User`. **Không bao giờ trả mật khẩu hay hash.**

Lỗi: `400` (validation), `409` (email đã tồn tại).

---

## Bước 3 – Đăng nhập (JWT)

### `POST /api/auth/login`

Request:
```json
{ "email": "an@example.com", "password": "matkhau123" }
```

Response `200 OK`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "x7Kp2v...",
  "user": { "id": "3f2b8c1e-9a4d-4e7b-8c21-5d6f0a1b2c3d", "email": "an@example.com", "displayName": "An", "createdAt": "..." }
}
```

- `accessToken`: JWT, sống ngắn (gợi ý 15 phút). Claim `sub` = id của user (dạng chuỗi), cùng `iat` và `exp`.
- `expiresIn`: số giây access token còn hạn. Frontend dựa vào số này để biết khi nào cần refresh.
- `refreshToken`: chuỗi ngẫu nhiên, sống dài (gợi ý 14 ngày). Chi tiết ở phần [Refresh token](#refresh-token).

Lỗi: `401` nếu sai email hoặc mật khẩu. Không nói rõ sai cái nào, để tránh lộ email nào đã đăng ký.

### `GET /api/me`

Response `200 OK`: `User` của người đang đăng nhập (lấy từ claim `sub` trong JWT).

---

## Refresh token

Access token sống ngắn để nếu bị lộ thì cũng nhanh hết hạn. Refresh token sống dài, chỉ dùng để
xin access token mới, nhờ vậy người dùng không phải đăng nhập lại mỗi 15 phút.

| | Access token | Refresh token |
|---|---|---|
| Dạng | JWT (backend kiểm tra bằng chữ ký, không cần DB) | Chuỗi ngẫu nhiên, **không phải JWT** |
| Sống | ~15 phút | ~14 ngày |
| Gửi ở đâu | Header `Authorization` mọi request | Chỉ body của `/auth/refresh` và `/auth/logout` |
| Thu hồi được? | Không (chờ hết hạn) | Có: xoá hoặc đánh dấu trong DB |

### `POST /api/auth/refresh`

Không cần header `Authorization`, vì lúc gọi endpoint này access token thường đã hết hạn.

Request:
```json
{ "refreshToken": "x7Kp2v..." }
```

Response `200 OK`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "Qm9r4a..."
}
```

- **Xoay vòng (rotation):** mỗi lần refresh, backend cấp refresh token **mới** và thu hồi cái cũ.
  Refresh token cũ dùng lại → `401`. Frontend đã đảm bảo chỉ gọi refresh một lần, kể cả khi
  nhiều request hay nhiều tab cùng hết hạn một lúc.
- Lỗi: `401` nếu refresh token không tồn tại, hết hạn hoặc đã bị thu hồi. Frontend nhận `401` sẽ đăng xuất.

### `POST /api/auth/logout`

Không cần header `Authorization`.

Request:
```json
{ "refreshToken": "x7Kp2v..." }
```

Response `200 OK`, `body: null`: thu hồi refresh token đó. Token không tồn tại cũng trả `200`.
Frontend đã tự xoá token phía trình duyệt, endpoint này chỉ để refresh token không dùng lại được nữa.

### Gợi ý phía backend

- Tạo refresh token: 32 byte từ `SecureRandom`, mã hoá Base64 URL-safe.
- **Chỉ lưu hash** (SHA-256) của refresh token trong DB, giống như không lưu mật khẩu gốc.
  Lộ DB thì kẻ tấn công cũng không có token để dùng.
- Bảng gợi ý:

```sql
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- `/auth/refresh`: tìm theo `token_hash`, kiểm tra `revoked_at IS NULL` và `expires_at > now()`,
  đặt `revoked_at = now()` cho token cũ, tạo token mới. Làm tất cả trong một `@Transactional`.

---

## Bước 4–5 – Hội thoại và tin nhắn

### `GET /api/conversations`

Query (không bắt buộc): `page` (đếm từ `1`, mặc định `1`), `size` (1–25, mặc định `20`).
Ví dụ: `GET /api/conversations?page=1&size=20`.

Response `200 OK`: `Conversation[]` của user hiện tại, **mới nhất trước** (`modifiedAt` giảm dần).

### `POST /api/conversations`

Request:
```json
{ "title": "Học Spring" }
```

- `title`: bắt buộc, 1–200 ký tự. Frontend tạo conversation lúc gửi tin nhắn đầu tiên, gửi `title` theo ngôn ngữ giao diện đang chọn ("Cuộc trò chuyện mới" / "New chat").

Response `201 Created`: `Conversation`.

Lỗi: `400` (`validation.required` khi thiếu `title`, `validation.titleTooLong` khi quá 200 ký tự).

### `PATCH /api/conversations/{id}` — đổi tên

Request:
```json
{ "title": "Học Spring Security" }
```

- `title`: bắt buộc, 1–200 ký tự (validation giống `POST`).
- **Không** cập nhật `modifiedAt`: đổi tên không làm conversation nhảy lên đầu danh sách, chỉ tin nhắn mới mới làm vậy.

Response `200 OK`: `Conversation` sau khi đổi tên. `404` nếu không thuộc user hiện tại.

### `DELETE /api/conversations/{id}`

Response `200 OK`, `body: null`. Xoá luôn các tin nhắn của conversation đó. `404` nếu không thuộc user hiện tại.

### `GET /api/conversations/{id}/messages`

Response `200 OK`: `Message[]`, **cũ nhất trước** (`createdAt` tăng dần).

### `POST /api/conversations/{id}/messages` — gửi tin nhắn

Request:
```json
{ "content": "Spring Boot là gì?" }
```

Frontend gửi header `Accept: text/event-stream, application/json`. Backend được chọn **một trong hai** kiểu trả lời. Frontend xử lý được cả hai, nên bạn làm kiểu A trước rồi nâng cấp lên kiểu B.

Việc backend phải làm:
1. Lưu tin nhắn của user.
2. Lấy lịch sử tin nhắn của conversation, gửi cho LLM.
3. Lưu câu trả lời của LLM thành tin nhắn `assistant`.
4. Cập nhật `modifiedAt` của conversation. (`title` đã có từ lúc tạo conversation, không cần đặt lại.)

#### Kiểu A — JSON, không stream (bước 4–5)

`200 OK`, `Content-Type: application/json`:
```json
{
  "userMessage":      { "id": "9b1c...", "role": "user",      "content": "Spring Boot là gì?", "createdAt": "..." },
  "assistantMessage": { "id": "c42e...", "role": "assistant", "content": "**Spring Boot** là ...", "createdAt": "..." }
}
```

#### Kiểu B — SSE stream (bước 6)

`200 OK`, `Content-Type: text/event-stream`. Stream **không** bọc envelope: mỗi `data:` là JSON trần như dưới đây.
Lỗi xảy ra **trước khi** stream bắt đầu (ví dụ `404`) vẫn trả JSON có envelope như mọi endpoint khác. Mỗi event có dạng `event:` + `data:` (JSON) + một dòng trống:

```
event: reasoning
data: {"text":"Người dùng hỏi về Spring Boot..."}

event: delta
data: {"text":"**Spring"}

event: delta
data: {"text":" Boot** là"}

event: done
data: {"id":"c42e...","role":"assistant","content":"**Spring Boot** là ...","reasoning":"Người dùng hỏi về Spring Boot...","createdAt":"..."}

```

| Event | `data` | Ý nghĩa |
|---|---|---|
| `reasoning` | `{"text": "..."}` | Một đoạn suy luận của model (chỉ model có reasoning mới gửi). Frontend nối dần vào khối "Đang suy nghĩ" |
| `delta` | `{"text": "..."}` | Một đoạn chữ mới. Frontend nối dần vào câu trả lời |
| `done` | `Message` | Câu trả lời đầy đủ, đã lưu DB. Kết thúc stream |
| `error` | `{"message": "..."}` | Lỗi giữa chừng (ví dụ LLM lỗi). Kết thúc stream |

Gợi ý phía Spring: `SseEmitter` với `SseEmitter.event().name("delta").data(...)`.

Người dùng bấm **Dừng** thì frontend huỷ request (`AbortController`). Phía Spring sẽ thấy kết nối bị đóng (`IOException` khi gửi tiếp), lúc đó nên ngừng đọc từ LLM.

---

## Thứ tự làm và endpoint cần có

| Bước | Endpoint | Giao diện dùng được |
|---|---|---|
| 2 | `POST /api/auth/register` | Trang đăng ký |
| 3 | `POST /api/auth/login`, `GET /api/me` | Đăng nhập, vào trang chat (danh sách trống, báo lỗi khi gửi tin) |
| 4–5 | 4 endpoint conversations/messages, trả kiểu A | Chat đầy đủ, câu trả lời hiện một lần |
| 4–5 | `PATCH /api/conversations/{id}` | Đổi tên conversation (menu ⋯) |
| 6 | Gửi tin nhắn trả kiểu B | Chữ hiện dần, nút Dừng |
| 7 | `POST /api/auth/refresh`, `POST /api/auth/logout` | Không bị đăng xuất sau khi access token hết hạn |
