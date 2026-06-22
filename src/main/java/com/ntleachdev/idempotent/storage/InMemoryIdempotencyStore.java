package com.ntleachdev.idempotent.storage;

import com.ntleachdev.idempotent.core.IdempotencyStore;
import com.ntleachdev.idempotent.core.StoredResponse;
import com.ntleachdev.idempotent.exception.IdempotentOperationInProgressException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of IdempotencyStore for testing and POC purposes.
 *
 * Not recommended for production use as it doesn't persist across restarts
 * and has no distributed lock semantics.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record CacheEntry(StoredResponse response, Instant expiresAt) {
            private CacheEntry(StoredResponse response, Duration expiresAt) {
                this(response, Instant.now().plus(expiresAt));
            }

            boolean isExpired() {
                return Instant.now().isAfter(expiresAt);
            }
        }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<StoredResponse> getIfCachedOrAcquireLock(final String key, final Duration ttl)
        throws IdempotentOperationInProgressException {
        var existing = cache.get(key);
        if (existing != null && existing.isExpired()) {
            cache.remove(key);
            existing = null;
        }

        if (existing == null) {
            var processingEntry = new CacheEntry(StoredResponse.processing(), ttl);
            cache.put(key, processingEntry);
            return Optional.empty();
        }

        final var response = existing.response;

        if (response.isProcessing()) {
            throw new IdempotentOperationInProgressException(
                "Request is currently being processed. Please retry shortly."
            );
        }

        return Optional.of(response);
    }

    @Override
    public void storeResponse(final String key, final StoredResponse response, final Duration ttl) {
        final var entry = new CacheEntry(response, ttl);
        cache.put(key, entry);
    }

    @Override
    public void releaseLock(final String key) {
        cache.remove(key);
    }
}


