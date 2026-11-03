package com.llmchad.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.llmchad.common.ApiResponse;
import com.llmchad.common.MessageKey;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    // @Valid thất bại: trả message key của trường lỗi đầu tiên (đặt trong annotation của DTO,
    // ví dụ @Size(message = MessageKey.PASSWORD_TOO_SHORT)). Frontend chỉ hiện một thông báo.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : MessageKey.VALIDATION_FAILED;
        return build(HttpStatus.BAD_REQUEST, message);
    }

    // Body không phải JSON hợp lệ, hoặc thiếu body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, MessageKey.MALFORMED_JSON);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        // Lỗi Spring tự ném ra đã mang sẵn HTTP status (404 sai đường dẫn, 405 sai
        // method, ResponseStatusException...)
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String message = switch (status.value()) {
                case 404 -> MessageKey.NOT_FOUND;
                case 405 -> MessageKey.METHOD_NOT_ALLOWED;
                default -> null; // frontend hiện câu mặc định theo HTTP status
            };
            return build(status, message);
        }

        // Lỗi không lường trước: ghi log đầy đủ, nhưng không lộ chi tiết cho client
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MessageKey.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status, message));
    }
}
