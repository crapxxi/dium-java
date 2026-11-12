package com.dium.demo.exceptions;

import lombok.Getter;

/**
 * Слишком частые запросы (например, повторная отправка кода раньше чем через 60 секунд). HTTP 429.
 */
@Getter
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
