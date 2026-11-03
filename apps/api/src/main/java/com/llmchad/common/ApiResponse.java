package com.llmchad.common;

import org.springframework.http.HttpStatusCode;

public record ApiResponse<T>(int status, String message, T data) {
    public static <T> ApiResponse<T> success(HttpStatusCode status, String message, T data) {
        return new ApiResponse<>(status.value(), message, data);
    }

    public static ApiResponse<Void> error(HttpStatusCode status, String message) {
        return new ApiResponse<>(status.value(), message, null);
    }
}
