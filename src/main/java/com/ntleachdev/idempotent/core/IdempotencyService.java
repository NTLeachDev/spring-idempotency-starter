package com.ntleachdev.idempotent.core;

import com.ntleachdev.idempotent.annotation.StoredDuration;
import com.ntleachdev.idempotent.exception.IdempotencySerializationException;
import com.ntleachdev.idempotent.exception.IdempotentOperationInProgressException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class IdempotencyService {

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    @Autowired
    public IdempotencyService(final ObjectMapper objectMapper,
                              final IdempotencyStore idempotencyStore) {
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
    }

    public Optional<HttpEntity<?>> getIfCachedOrAcquireLock(final String idempotencyKey, final Duration ttl)
        throws IdempotentOperationInProgressException {
        var result = idempotencyStore.getIfCachedOrAcquireLock(idempotencyKey, ttl);

        if (result.isPresent()) {
            var response = result.get();
            if (response.isProcessing()) {
                throw new IdempotentOperationInProgressException(
                    "Request with idempotency key is currently being processed. Please retry shortly."
                );
            }
            return Optional.of(StoredResponse.toResponseEntity(response));
        }

        return Optional.empty();
    }

    public void markAsComplete(final String idempotencyKey, final ResponseEntity<?> result, final StoredDuration duration) {
        final StoredResponse response = buildStoredResponse(result);
        idempotencyStore.storeResponse(idempotencyKey, response, duration.scale().toDuration(duration.value()));
        releaseLock(idempotencyKey);
    }

    public void releaseLock(final String idempotencyKey) {
        idempotencyStore.releaseLock(idempotencyKey);
    }

    private StoredResponse buildStoredResponse(final ResponseEntity<?> response) {
        if (response == null) {
            return StoredResponse.empty();
        }

        final HttpHeaders headers = response.getHeaders();
        final Map<String, List<String>> headerMap = headers != null
                ? headers.headerSet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue())
                ))
                : Map.of();

        return new StoredResponse(
                response.getStatusCode().value(),
                headerMap,
                serializeBody(response.getBody()),
                Instant.now()
        );
    }

    private byte[] serializeBody(final Object body) {
        if (body == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JacksonException e) {
            throw new IdempotencySerializationException("Failed to serialize response body", e);
        }
    }
}