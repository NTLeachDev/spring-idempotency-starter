package com.ntleachdev.idempotent.core;

import java.time.Instant;

public record StoredResponse(
        int statusCode,
        String contentType,
        byte[] body,
        Instant createdAt
) {}