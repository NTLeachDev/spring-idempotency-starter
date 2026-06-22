package com.ntleachdev.idempotent.core;

import com.ntleachdev.idempotent.exception.IdempotentOperationInProgressException;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {

    /**
     * Atomically attempts to get a cached response or acquire a processing lock.
     *
     * Returns:
     * - Optional with StoredResponse if a cached response exists and not processing
     * - Optional.empty() if the lock was successfully acquired by this call
     *
     * Throws:
     * - IdempotentOperationInProgressException if the lock is currently held by another request
     */
    Optional<StoredResponse> getIfCachedOrAcquireLock(final String key, final Duration ttl)
        throws IdempotentOperationInProgressException;

    void storeResponse(final String key, final StoredResponse response, final Duration ttl);

    void releaseLock(final String key);
}

