-- Nguồn web (Tavily) mà model đã dùng để trả lời: [{"title": "...", "url": "..."}]. Chỉ có ở tin assistant, NULL nếu không search.
ALTER TABLE messages ADD COLUMN sources JSONB;
