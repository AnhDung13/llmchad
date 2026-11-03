package com.llmchad.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record Paging(@Min(1) @Max(999) Integer size, @Min(1) Integer page) {
    public Paging {
        if (size == null)
            size = 20;
        if (page == null)
            page = 1;
    }
}
