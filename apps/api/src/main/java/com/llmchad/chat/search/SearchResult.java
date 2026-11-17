package com.llmchad.chat.search;

/** Một kết quả web search (Tavily, TinyFish...). `content` là đoạn trích, chỉ gửi cho model, không lưu DB. */
public record SearchResult(String title, String url, String content) {
}
