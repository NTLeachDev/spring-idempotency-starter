package com.ntleachdev.idempotent.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class IdempotencyService {

    private final IdempotencyStore idempotencyStore;
    private final Clock clock;

    @Autowired
    public IdempotencyService(final IdempotencyStore idempotencyStore,
                              final Clock clock) {
        this.idempotencyStore = idempotencyStore;
        this.clock = clock;
    }

    /**
     * Query the {@link IdempotencyStore} for an existing response or acquire a processing lock.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>If the store returns {@link GetResult.Status#ACQUIRED} the caller has acquired the processing
     *       sentinel and should proceed to execute the operation. After producing the response the caller
     *     must call {@link #markAsComplete(String, int, Map, byte[], java.time.Instant, Duration)} to atomically
     *     replace the sentinel with a stored response.</li>
        *   <li>If the store returns {@link GetResult.Status#REPLAY} the returned {@link GetResult#response()} will
     *       contain a {@link StoredResponse} which may be either a PROCESSING sentinel (operation in progress)
     *     or a STORED value (previous response). Callers should treat PROCESSING as "in progress" (typically
     *     mapped to HTTP 409 / gRPC ALREADY_EXISTS) and STORED as a cached response to be replayed.</li>
     * </ul>
     *
     * @param idempotencyKey the idempotency key provided by the client
     * @param lockTtl duration that the processing sentinel will live (lock TTL)
     * @param responseTtl desired TTL for the final stored response
     * @return a {@link GetResult} indicating whether the caller acquired the lock or should replay an existing
     *     response (possibly in-progress)
     */
    public GetResult getIfCachedOrAcquireLock(final String idempotencyKey, final Duration lockTtl, final Duration responseTtl) {
        return idempotencyStore.getIfCachedOrAcquireLock(idempotencyKey, lockTtl, responseTtl);
    }

    /**
     * Stores the already-captured StoredResponse and replaces the processing sentinel.
     */
    public boolean markAsComplete(final String idempotencyKey,
                                  final int statusCode,
                                  final Map<String, List<String>> headers,
                                  final byte[] body,
                                  final Instant createdAt,
                                  final Duration ttl) {
        // build a StoredResponse with expiry
        final var expiresAt = Instant.now(clock).plus(ttl);
        final var stored = StoredResponse.stored(statusCode, headers, body, createdAt, expiresAt);
        return idempotencyStore.storeResponse(idempotencyKey, stored);
    }

    public void releaseLock(final String idempotencyKey) {
        idempotencyStore.releaseLock(idempotencyKey);
    }
}