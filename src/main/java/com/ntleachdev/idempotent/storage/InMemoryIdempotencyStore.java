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

    private static class CacheEntry {
        final StoredResponse response;
        final Instant expiresAt;

        CacheEntry(StoredResponse response, Duration ttl) {
            this.response = response;
            this.expiresAt = Instant.now().plus(ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredResponse> get(final String key) {
        final var entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.response);
    }

    @Override
    public boolean tryAcquire(final String key, final Duration ttl) {
        // Try to set a processing lock
        final var processingEntry = new CacheEntry(StoredResponse.processing(), ttl);
        return cache.putIfAbsent(key, processingEntry) == null;
    }

    @Override
    public synchronized Optional<StoredResponse> getIfCachedOrAcquireLock(final String key, final Duration ttl)
        throws IdempotentOperationInProgressException {
        // Clean up expired entries
        var existing = cache.get(key);
        if (existing != null && existing.isExpired()) {
            cache.remove(key);
            existing = null;
        }

        if (existing == null) {
            // No entry exists, acquire the lock by storing PROCESSING sentinel
            var processingEntry = new CacheEntry(StoredResponse.processing(), ttl);
            cache.put(key, processingEntry);
            return Optional.empty(); // Signal: lock acquired, proceed with processing
        }

        final var response = existing.response;

        if (response.isProcessing()) {
            // Lock is held by another request, reject this one
            throw new IdempotentOperationInProgressException(
                "Request is currently being processed. Please retry shortly."
            );
        }

        // A cached response exists, return it
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


