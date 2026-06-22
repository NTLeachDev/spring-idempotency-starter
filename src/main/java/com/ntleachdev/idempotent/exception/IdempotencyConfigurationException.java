package com.ntleachdev.idempotent.exception;

/**
 * Thrown when an idempotent method is misconfigured (e.g., doesn't return ResponseEntity).
 */
public class IdempotencyConfigurationException extends RuntimeException {
    public IdempotencyConfigurationException(final String message) {
        super(message);
    }

    public IdempotencyConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

