package com.albudoor.hms.platform.exception;

/**
 * Thrown when an account is temporarily locked after too many consecutive failed
 * login attempts. Mapped to HTTP 429 Too Many Requests.
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
