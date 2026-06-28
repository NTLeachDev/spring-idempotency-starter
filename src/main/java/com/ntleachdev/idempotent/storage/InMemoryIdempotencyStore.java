package com.ntleachdev.idempotent.storage;

import com.ntleachdev.idempotent.core.GetResult;
import com.ntleachdev.idempotent.core.IdempotencyStore;
import com.ntleachdev.idempotent.core.StoredResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of IdempotencyStore for testing and POC purposes.
 *
 * Not recommended for production use as it doesn't persist across restarts
 * and has no distributed lock semantics.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record CacheEntry(StoredResponse response) {
        boolean isExpired() {
            return response.isExpired(Instant.now());
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public GetResult getIfCachedOrAcquireLock(final String key, final Duration lockTtl, final Duration responseTtl) {
        final Instant now = Instant.now();
        var existing = cache.get(key);
        if (existing != null && existing.isExpired()) {
            cache.remove(key, existing);
            existing = null;
        }

        if (existing == null) {
            final var processing = StoredResponse.processing(now.plus(lockTtl));
            final var processingEntry = new CacheEntry(processing);
            final var prev = cache.putIfAbsent(key, processingEntry);
            if (prev == null) {
                // we acquired the lock
                return GetResult.acquired();
            }
            existing = prev;
        }

        final var response = existing.response();
        // if processing or stored, return replay with the current response
        return GetResult.replay(response);
    }

    @Override
    public boolean storeResponse(final String key, final StoredResponse response) {
        // Try to replace the processing sentinel with the final stored response
        final int maxAttempts = 3;
        for (int i = 0; i < maxAttempts; i++) {
            var existing = cache.get(key);
            if (existing == null || existing.isExpired()) {
                cache.put(key, new CacheEntry(response));
                return true;
            }

            final var existingResp = existing.response();
            if (existingResp.isProcessing()) {
                // attempt to replace sentinel atomically
                final boolean replaced = cache.replace(key, existing, new CacheEntry(response));
                if (replaced) return true;
                // else loop and retry
            } else {
                // already stored - nothing to do
                return true;
            }
        }
        // failed to replace after retries; check if current is stored
        var current = cache.get(key);
        return current != null && current.response().isStored();
    }

    @Override
    public void releaseLock(final String key) {
        var existing = cache.get(key);
        if (existing != null && existing.response().isProcessing()) {
            cache.remove(key, existing);
        }
    }
}


