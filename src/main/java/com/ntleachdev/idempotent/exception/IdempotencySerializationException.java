package com.ntleachdev.idempotent.exception;

/**
 * Thrown when serialization or deserialization of a response body fails.
 */
public class IdempotencySerializationException extends RuntimeException {
    public IdempotencySerializationException(final String message) {
        super(message);
    }

    public IdempotencySerializationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

