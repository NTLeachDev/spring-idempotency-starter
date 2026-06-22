package com.ntleachdev.idempotent.exception;

/**
 * Thrown when an idempotency key does not match the required format.
 */
public class IdempotencyKeyFormatException extends RuntimeException {
    public IdempotencyKeyFormatException(final String message) {
        super(message);
    }

    public IdempotencyKeyFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

