package com.llmchad.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;

import com.llmchad.exception.AppException;

/** Helper dùng chung cho test: kiểm tra một đoạn code ném AppException với status + message key mong đợi. */
public final class TestAssertions {
    private TestAssertions() {
    }

    public static void assertAppException(ThrowingCallable call, HttpStatus status, String messageKey) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getMessage()).isEqualTo(messageKey);
                });
    }
}
