package com.ntleachdev.idempotent.core;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {
    Optional<StoredResponse> get(final String key);

    boolean tryAcquire(final String key, final Duration ttl);

    void storeResponse(final String key, final StoredResponse response, final Duration ttl);

    void release(final String key);
}

