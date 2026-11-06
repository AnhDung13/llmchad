# API contract — llmchad

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

Mọi endpoint **trừ** các endpoint `POST /api/auth/register`, `/login`, `/refresh`, `/logout`,
`/verify-email`, `/resend-verification`, `/oauth/exchange` và các đường dẫn OAuth của Spring
(`/api/oauth2/authorization/*`, `/api/login/oauth2/code/*`, xem [Bước 9](#bước-9--đăng-nhập-google--github)) đều cần header:

```
Authorization: Bearer <accessToken>
```

Thiếu token, token sai hoặc token hết hạn → `401`. Khi nhận `401`, frontend gọi `POST /api/auth/refresh`
một lần rồi gửi lại request. Refresh cũng thất bại thì frontend đăng xuất và đưa người dùng về trang đăng nhập.

## Envelope

Mọi response JSON, cả thành công lẫn lỗi, có cùng một dạng (`com.llmchad.common.ApiResponse`):

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

- Danh sách key: `apps/api/src/main/java/com/llmchad/common/MessageKey.java`
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
| `auth.emailVerified` | 200 | Nhập đúng mã: đã tạo tài khoản và đăng nhập |
| `auth.verificationSent` | 200 | Đã gửi lại mã xác minh |
| `auth.invalidVerifyCode` | 422 / 400 | Sai mã (422), hoặc mã không đủ 6 ký tự (400, validation) |
| `auth.verificationExpired` | 404 | Không còn bản đăng ký chờ xác minh (quá 15 phút, hoặc chưa từng đăng ký): phải đăng ký lại |
| `auth.verifyCodeExpired` | 410 | Mã quá 3 phút: bấm gửi lại để nhận mã mới (không phải đăng ký lại) |
| `auth.verifyAttemptsExceeded` | 429 | Sai mã 5 lần: bản đăng ký bị xoá, phải đăng ký lại |
| `auth.tooManyRequests` | 429 | Gửi mã (đăng ký hoặc gửi lại) khi chưa qua 60 giây kể từ lần trước |
| `auth.sendMailFailed` | 502 | SMTP lỗi, không gửi được mail |
| `auth.invalidOAuthCode` | 401 | Mã OAuth dùng 1 lần sai, hết hạn hoặc đã dùng |
| `auth.oauthFailed` | — | Dùng trong redirect `/login?oauthError=...` khi đăng nhập Google/GitHub lỗi |
| `auth.oauthEmailUnverified` | — | Như trên, khi provider không trả email đã xác minh |
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

Response `201 Created`, `message: "auth.registerSuccess"`:
```json
{ "expiresIn": 300, "resendAfter": 60 }
```
- `expiresIn`: số giây mã còn hiệu lực (3 phút). Frontend đếm ngược theo số này. Bản đăng ký thì sống 15 phút,
  trong thời gian đó mã hết hạn chỉ cần gửi lại.
- `resendAfter`: số giây phải chờ trước khi được gửi lại mã.


- **Chưa tạo user trong DB.** Backend lưu bản đăng ký tạm vào Redis và gửi mã xác minh 6 ký tự tới email
  (xem [Bước 8](#bước-8--xác-minh-email)). Nhập đúng mã mới tạo user.
- Frontend hiện màn "Kiểm tra hộp thư" để nhập mã. Đăng ký lại cùng email trước khi xác minh thì bản cũ bị
  ghi đè (mã cũ hết hiệu lực).

Lỗi: `400` (validation), `409` (email đã tồn tại), `429 auth.tooManyRequests` (chưa qua 60 giây kể từ lần gửi
mã trước), `502 auth.sendMailFailed`.

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

Lỗi: `401 auth.invalidCredentials` nếu sai email hoặc mật khẩu, **hoặc user không có mật khẩu** (chỉ đăng nhập
bằng Google/GitHub). Không nói rõ sai cái nào, để tránh lộ email nào đã đăng ký.

Email được chuẩn hoá (`trim` + chữ thường) ở mọi endpoint auth: `An@Example.com` và `an@example.com` là một tài khoản.

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

## Bước 8 – Xác minh email

Luồng: `POST /auth/register` → backend gửi mã 6 ký tự (số + chữ hoa + chữ thường, **phân biệt hoa thường**)
tới email → người dùng nhập mã ở màn "Kiểm tra hộp thư" → `POST /auth/verify-email` → backend tạo user và
**đăng nhập luôn**.

Trong DB chỉ có tài khoản đã xác minh, nên bảng `users` không cần cột trạng thái xác minh.

### `POST /api/auth/verify-email`

Không cần header `Authorization`.

Request:
```json
{ "email": "an@example.com", "password": "matkhau123", "code": "a7Kp2Q" }
```

`password` là mật khẩu vừa nhập ở form đăng ký. `register` không cần mã nên ai cũng đăng ký đè được bản đăng ký
đang chờ (bằng mật khẩu của họ, mã mới vẫn gửi về chủ email). Kiểm tra mật khẩu ở bước này để chủ email không vô tình
tạo tài khoản với mật khẩu của kẻ khác. Backend chỉ kiểm tra mật khẩu **sau khi đúng mã**, nên không dò được mật khẩu.

Response `200 OK`, `message: "auth.emailVerified"`: **giống hệt** response của `POST /api/auth/login`
(access token, refresh token, user).

Lỗi:
- `400` validation (`code` không đủ 6 ký tự → `auth.invalidVerifyCode`).
- `422 auth.invalidVerifyCode`: sai mã. Mỗi lần sai tính một lượt.
- `429 auth.verifyAttemptsExceeded`: sai lượt thứ 5. Bản đăng ký bị xoá, frontend đưa về form đăng ký.
- `410 auth.verifyCodeExpired`: mã quá 3 phút. Không tính là một lượt sai; bấm gửi lại để nhận mã mới.
- `404 auth.verificationExpired`: không còn bản đăng ký (quá 15 phút, đã xác minh rồi, hoặc chưa đăng ký),
  hoặc đúng mã nhưng sai mật khẩu (bản đăng ký đã bị đăng ký đè, bị xoá luôn).
  Frontend đưa về form đăng ký.
- `409 auth.emailAlreadyExists`: email vừa được dùng ở chỗ khác trong lúc chờ (vd đăng nhập Google).

### `POST /api/auth/resend-verification`

Không cần header `Authorization`.

Request:
```json
{ "email": "an@example.com" }
```

Response `200 OK`, `message: "auth.verificationSent"`, `data` giống register (`{ "expiresIn": 300, "resendAfter": 60 }`):
tạo mã mới sống 3 phút (mã cũ hết hiệu lực), đặt lại số lượt thử, gia hạn bản đăng ký thêm 15 phút.

Lỗi: `404 auth.verificationExpired`, `429 auth.tooManyRequests` (chưa qua 60 giây kể từ lần gửi trước),
`502 auth.sendMailFailed`. Frontend khoá nút gửi lại 60 giây sau mỗi lần gửi.

Gửi mail lỗi (`502`, ở cả register lẫn resend) thì backend bỏ thời gian chờ 60 giây để thử lại được ngay. Resend gửi
mail trước rồi mới thay mã, nên gửi lỗi thì mã cũ vẫn dùng được.

### Phía backend (đã làm, `AuthService`)

| Redis key | Giá trị | TTL |
|---|---|---|
| `pending_register:{email}` | hash `{passwordHash (BCrypt), displayName, codeHash (SHA-256), attempts, codeExpiresAt (epoch ms)}` | 15 phút |
| `resend_verification:{email}` | `"1"`, tạo bằng `SET NX` | 60 giây |

- Mã sinh bằng `RandomCode.generate(6)` (`SecureRandom`), chỉ lưu hash (`HashUtils.sha256`), so sánh bằng
  `MessageDigest.isEqual`.
- Xác minh đúng: `DELETE` key trước khi insert. Hai request đúng mã cùng lúc thì chỉ request xoá được key mới tạo user.

---

## Bước 9 – Đăng nhập Google / GitHub

Dùng `spring-boot-starter-security-oauth2-client` (`http.oauth2Login(...)`). Luồng:

```
[Nút Google]  trình duyệt chuyển trang (không phải fetch) tới
  GET /api/oauth2/authorization/google            ← Spring lo, redirect sang Google
  → Google hỏi người dùng → redirect về
  GET /api/login/oauth2/code/google?code=&state=  ← Spring lo, đổi code lấy thông tin user
  → success handler (của bạn): tìm/tạo user, tạo mã dùng 1 lần
  → 302 /oauth/callback?code=<mã>                 ← trang frontend
  → frontend gọi POST /api/auth/oauth/exchange { code } → token như /login
```

`{provider}` là `google` hoặc `github` (registrationId của Spring).

### Redirect URI đăng ký ở Google / GitHub

| Môi trường | Redirect URI |
|---|---|
| Dev | `http://localhost:5173/api/login/oauth2/code/{provider}` (đi qua Vite proxy) |
| Prod | `https://llmchad.site/api/login/oauth2/code/{provider}` |

GitHub OAuth App chỉ nhận 1 callback URL → tạo 2 app (dev, prod). Google cho nhiều URI trong 1 client.

Đường dẫn mặc định của Spring không có `/api` (bị Caddy bỏ qua): phải đổi `authorizationEndpoint().baseUri(...)`
và `redirectionEndpoint().baseUri(...)`, và `redirect-uri={baseUrl}/api/login/oauth2/code/{registrationId}`.
Backend sau proxy cần `server.forward-headers-strategy=framework` để `{baseUrl}` ra đúng host trình duyệt thấy
(Vite đã bật `xfwd`, Caddy tự gửi `X-Forwarded-*`).

### Sau khi provider xác nhận (success handler)

1. Lấy `provider_user_id`: Google là claim `sub`, GitHub là field `id`. Lấy email:
   Google có `email` + `email_verified`; GitHub có thể trả `email: null` → gọi `GET https://api.github.com/user/emails`
   (scope `user:email`) và lấy email `primary && verified`.
2. Không có email đã xác minh → redirect `/login?oauthError=auth.oauthEmailUnverified`.
3. Có dòng `user_identities (provider, provider_user_id)` → dùng user đó.
4. Không có → tìm user theo email:
   - Có: thêm dòng `user_identities`. **Cẩn thận với user tạo trước Bước 8**: họ chưa từng xác minh email, có thể là
     kẻ gian đăng ký trước bằng email của nạn nhân. Liên kết vào tài khoản đó thì kẻ gian vẫn đăng nhập được bằng mật
     khẩu của hắn. Cách xử lý: khi liên kết, nếu user có `password_hash` và `created_at` trước ngày deploy Bước 8
     thì đặt `password_hash = NULL` (chủ email đặt lại mật khẩu sau).
   - Không có: tạo user (`password_hash = NULL`, `display_name` từ `name`,
     GitHub thiếu `name` thì dùng `login`, cắt còn 50 ký tự) + dòng `user_identities`.
5. Tạo mã dùng 1 lần: Redis `oauth_code:{sha256(code)}` → `userId`, TTL 60 giây.
6. `302` tới `/oauth/callback?code=<mã>` (đường dẫn tương đối, chạy được cả dev lẫn prod).

Lỗi bất kỳ (người dùng bấm Huỷ ở Google, ...) → failure handler `302` tới `/login?oauthError=auth.oauthFailed`.
Frontend đọc `oauthError` (là message key) và hiện toast.

**Không** đặt access/refresh token lên URL: URL nằm lại trong lịch sử trình duyệt và log của proxy.

### `POST /api/auth/oauth/exchange`

Không cần header `Authorization`.

Request:
```json
{ "code": "Zx8q..." }
```

Response `200 OK`, `message: "auth.loginSuccess"`: **giống hệt** response của `POST /api/auth/login`.

Lỗi: `401 auth.invalidOAuthCode` nếu mã không tồn tại, hết hạn hoặc đã dùng (`getAndDelete`).

### Lưu ý cấu hình Security

`oauth2Login()` khiến request API chưa đăng nhập có thể bị redirect `302` tới trang login thay vì `401`,
mà frontend dựa vào `401` để refresh token. Tách 2 `SecurityFilterChain`:
- `@Order(1)`, `securityMatcher("/api/oauth2/**", "/api/login/oauth2/**")`: `oauth2Login`, cho phép session
  (Spring lưu `state` trong session giữa lúc đi và lúc về).
- `@Order(2)`: chain stateless hiện tại (resource server JWT).

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
| 8 | `POST /api/auth/verify-email`, `POST /api/auth/resend-verification` | Màn "Kiểm tra hộp thư" (nhập mã) sau khi đăng ký |
| 9 | OAuth của Spring + `POST /api/auth/oauth/exchange` | Nút Google / GitHub, trang `/oauth/callback` |
