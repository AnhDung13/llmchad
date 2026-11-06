-- User đăng nhập bằng OAuth (Google, GitHub) không có mật khẩu
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Một user có thể liên kết nhiều tài khoản OAuth (cùng email: vừa Google vừa GitHub)
CREATE TABLE user_identities (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- registrationId trong Spring: 'google' | 'github'
    provider         VARCHAR(20)  NOT NULL,
    -- Google: claim `sub`; GitHub: field `id` (số, lưu dạng chuỗi)
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_identities_user_id ON user_identities (user_id);
