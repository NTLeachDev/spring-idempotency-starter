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
        Instant createdAt
) {
    private static final StoredResponse EMPTY = new StoredResponse(0, null, null, Instant.EPOCH);
    private static final StoredResponse PROCESSING = new StoredResponse(-1, null, null, Instant.EPOCH);

    public static StoredResponse empty() {
        return EMPTY;
    }

    public static StoredResponse processing() {
        return PROCESSING;
    }

    /**
     * Returns true if this response is a sentinel indicating a lock is being held.
     */
    public boolean isProcessing() {
        return statusCode == -1;
    }

    public static ResponseEntity<?> toResponseEntity(final StoredResponse response) {
        if (response == null || response.statusCode() == 0) {
            return ResponseEntity.noContent().build();
        }

        // Do not convert PROCESSING sentinel to response
        if (response.isProcessing()) {
            throw new IllegalStateException("Cannot convert PROCESSING sentinel to ResponseEntity");
        }

        final var headers = new HttpHeaders();
        if (response.headers() != null) {
            response.headers().forEach(headers::put);
        }

        return ResponseEntity.status(response.statusCode())
                .headers(headers)
                .body(response.body());
    }
}