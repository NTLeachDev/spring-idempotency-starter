package com.ntleachdev.idempotent.core;

import java.time.Duration;

public interface IdempotencyStore {

    /**
     * Atomically attempts to get a cached response or acquire a processing lock.
     *
     * Returns a {@link GetResult} which indicates whether the caller has acquired the lock
     * (Status.ACQUIRED) or should replay an existing response (Status.REPLAY). The replayed
     * response may be in PROCESSING state to indicate an in-progress operation.
     */
    GetResult getIfCachedOrAcquireLock(final String key, final Duration lockTtl, final Duration responseTtl);

    /**
     * Atomically stores the final response for the key. Implementations should attempt to
     * replace the processing sentinel with the provided stored response. Returns true if the
     * replacement succeeded or response already present; false if replacement failed due to race.
     */
    boolean storeResponse(final String key, final StoredResponse response);

    /**
     * Best-effort removal of a processing sentinel. Implementations may no-op if a stored
     * response already exists.
     */
    void releaseLock(final String key);
}

