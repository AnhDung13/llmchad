package com.llmchad.exception;

import org.springframework.http.HttpStatus;

// Lỗi nghiệp vụ do mình chủ động ném ra
public class AppException extends RuntimeException {
    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;

    }

    public HttpStatus getStatus() {
        return status;
    }
}
