package com.ntleachdev.idempotent.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Service
public class IdempotencyService {

    private static final Duration TTL_PROCESSING = Duration.ofMinutes(5);
    private static final Duration TTL_COMPLETED = Duration.ofDays(1);
    private static final int KEY_LENGTH = 32;
    private static final String PROCESSING_STATE = "PROCESSING";

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    @Autowired
    public IdempotencyService(final ObjectMapper objectMapper,
                              final IdempotencyStore idempotencyStore) {
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
    }

    public boolean tryProcessingRequest(final String idempotencyKey) {
        throw new UnsupportedOperationException("IdempotencyService#tryProcessingRequest is not yet implemented");
    }

    public Object getStoredResponse(final String idempotencyKey, final ResolvableType returnType) {
        throw new UnsupportedOperationException("IdempotencyService#getStoredResponse is not yet implemented");
    }

    public void markAsComplete(final String idempotencyKey, final Object result) {
        throw new UnsupportedOperationException("IdempotencyService#markAsComplete is not yet implemented");
    }

    public void evictLock(final String idempotencyKey) {
        throw new UnsupportedOperationException("IdempotencyService#evictLock is not yet implemented");
    }

}