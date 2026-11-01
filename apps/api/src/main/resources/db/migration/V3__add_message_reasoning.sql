-- Phần suy luận (reasoning) của model, chỉ có ở tin nhắn assistant. NULL nếu model không trả reasoning.
ALTER TABLE messages ADD COLUMN reasoning TEXT;
