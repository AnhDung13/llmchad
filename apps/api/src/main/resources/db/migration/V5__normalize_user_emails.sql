-- Backend giờ chuẩn hoá email (trim + chữ thường) trước khi tìm hoặc lưu.
-- Đưa các tài khoản cũ về cùng dạng, nếu không người đăng ký bằng "An@Example.com" sẽ không đăng nhập được.
-- Nếu có hai tài khoản chỉ khác nhau chữ hoa/thường, migration lỗi UNIQUE: phải gộp tay trước khi deploy.
UPDATE users SET email = lower(trim(email)) WHERE email <> lower(trim(email));
