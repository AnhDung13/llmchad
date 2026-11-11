-- Đánh dấu user nào đã chứng minh là chủ email.
-- NULL = chưa từng xác minh: chỉ có ở tài khoản tạo trước khi có bước xác minh email (lúc đó ai cũng đăng ký được
--        bằng email của người khác). Liên kết Google/GitHub vào tài khoản này thì xoá mật khẩu (xem OAuthLoginService).
-- Thêm cột không kèm DEFAULT → các dòng đang có giữ NULL; sau đó mới đặt DEFAULT now() cho các dòng thêm về sau.
-- Mọi cách tạo user hiện nay (nhập đúng mã xác minh, đăng nhập Google/GitHub) đều đã xác minh email, nên DEFAULT now() đúng.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE users ALTER COLUMN email_verified_at SET DEFAULT now();
