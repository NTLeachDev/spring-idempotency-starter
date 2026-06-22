package com.ntleachdev.idempotent.exception;

/**
 * Thrown when a required idempotency key is missing from the request.
 */
public class IdempotencyKeyMissingException extends RuntimeException {
    public IdempotencyKeyMissingException(final String message) {
        super(message);
    }

    public IdempotencyKeyMissingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

