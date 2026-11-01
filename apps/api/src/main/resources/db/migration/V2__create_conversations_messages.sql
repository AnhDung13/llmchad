CREATE TABLE conversations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- GET /api/conversations: lọc theo user, sắp xếp modified_at giảm dần
CREATE INDEX idx_conversations_user_modified ON conversations (user_id, modified_at DESC);

CREATE TABLE messages (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID         NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(16)  NOT NULL CHECK (role IN ('user', 'assistant')),
    content          TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- GET /api/conversations/{id}/messages: lọc theo conversation, cũ nhất trước (ORDER BY created_at).
-- UUID ngẫu nhiên nên không sắp xếp theo id được.
CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);
