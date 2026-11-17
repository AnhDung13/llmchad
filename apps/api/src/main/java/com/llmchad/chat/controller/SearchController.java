package com.llmchad.chat.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.llmchad.chat.search.WebSearchProviders;
import com.llmchad.common.ApiResponse;

@RestController
@RequestMapping("/search")
public class SearchController {
    private final WebSearchProviders providers;

    public SearchController(WebSearchProviders providers) {
        this.providers = providers;
    }

    /** Các provider đã có API key, để frontend chỉ hiện những lựa chọn dùng được */
    @GetMapping("/providers")
    public ApiResponse<List<String>> providers() {
        return ApiResponse.success(HttpStatus.OK, null, providers.enabledIds());
    }
}
