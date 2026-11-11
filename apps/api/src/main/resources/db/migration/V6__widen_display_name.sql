-- Tên lấy từ Google/GitHub có thể dài hơn 50 ký tự. Nới lên 256 (đếm theo ký tự, không phải byte).
-- Tăng độ dài VARCHAR không phải viết lại bảng, chạy tức thì.
ALTER TABLE users ALTER COLUMN display_name TYPE VARCHAR(256);
