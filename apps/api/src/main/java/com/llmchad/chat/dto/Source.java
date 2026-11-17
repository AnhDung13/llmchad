package com.llmchad.chat.dto;

/** Một trang web model đã đọc, gửi cho frontend (event `sources`) và lưu vào messages.sources */
public record Source(String title, String url) {
}
