package com.ntleachdev.idempotent.core;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StoredResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body,
        Instant createdAt,
        State state,
        Instant expiresAt
) {
    public enum State { PROCESSING, STORED }

    private static final StoredResponse EMPTY = new StoredResponse(0, null, null, Instant.EPOCH, State.STORED, Instant.EPOCH);

    public static StoredResponse empty() {
        return EMPTY;
    }

    public static StoredResponse processing(final Instant expiresAt) {
        return new StoredResponse(-1, null, null, Instant.now(), State.PROCESSING, expiresAt);
    }

    public static StoredResponse stored(final int statusCode, final Map<String, List<String>> headers,
                                        final byte[] body, final Instant createdAt, final Instant expiresAt) {
        return new StoredResponse(statusCode, headers, body, createdAt, State.STORED, expiresAt);
    }

    /**
     * Returns true if this response is a sentinel indicating a lock is being held.
     */
    public boolean isProcessing() {
        return state == State.PROCESSING;
    }

    public boolean isStored() {
        return state == State.STORED;
    }

    public boolean isExpired(final Instant now) {
        return now.isAfter(expiresAt);
    }

    /**
     * Convert stored response into ResponseEntity<byte[]> to avoid re-serialization.
     */
    public static ResponseEntity<byte[]> toResponseEntity(final StoredResponse response) {
        if (response == null || response.statusCode() == 0) {
            return ResponseEntity.noContent().build();
        }

        // Do not convert PROCESSING sentinel to response
        if (response.isProcessing()) {
            throw new IllegalStateException("Cannot convert PROCESSING sentinel to ResponseEntity");
        }

        final var headers = new HttpHeaders();
        if (response.headers() != null) {
            response.headers().forEach((k, v) -> {
                if (!"Content-Length".equalsIgnoreCase(k)) { // let container compute length
                    headers.put(k, List.copyOf(v));
                }
            });
        }

        return ResponseEntity.status(response.statusCode())
                .headers(headers)
                .body(response.body());
    }
}