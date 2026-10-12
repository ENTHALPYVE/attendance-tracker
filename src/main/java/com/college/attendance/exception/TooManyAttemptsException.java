package com.college.attendance.exception;

import java.time.Duration;

/** Too many wrong passwords in a short time. Returned as HTTP 429 with a Retry-After header. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
