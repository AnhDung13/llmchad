package com.llmchad.chat.dto;

import java.util.List;

public record LLMRequestDTO(String model, Boolean stream, List<LLMMessage> messages) {

}
