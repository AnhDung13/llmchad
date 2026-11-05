package com.llmchad.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record Paging(
        @Min(value = 1, message = MessageKey.PAGE_SIZE_INVALID) @Max(value = 999, message = MessageKey.PAGE_SIZE_INVALID) Integer size,
        @Min(value = 1, message = MessageKey.PAGE_INVALID) Integer page) {
    public Paging {
        if (size == null)
            size = 20;
        if (page == null)
            page = 1;
    }
}
