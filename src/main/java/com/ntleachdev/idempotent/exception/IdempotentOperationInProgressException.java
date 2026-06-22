package com.ntleachdev.idempotent.exception;

/**
 * Thrown when a duplicate idempotent request is received while the original is still being processed.
 */
public class IdempotentOperationInProgressException extends RuntimeException {
    public IdempotentOperationInProgressException(final String message) {
        super(message);
    }

    public IdempotentOperationInProgressException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

