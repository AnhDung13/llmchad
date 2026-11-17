package com.llmchad.chat.dto;

/** Một tool call trong tin assistant, đúng format OpenAI: {"id", "type": "function", "function": {"name", "arguments"}} */
public record ToolCall(String id, String type, Function function) {

    /** `arguments` là chuỗi JSON do model sinh ra, ví dụ "{\"query\":\"...\"}" */
    public record Function(String name, String arguments) {
    }

    public static ToolCall function(String id, String name, String arguments) {
        return new ToolCall(id, "function", new Function(name, arguments));
    }
}
