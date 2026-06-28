package com.ntleachdev.idempotent.interceptor;

import com.ntleachdev.idempotent.annotation.Idempotent;
import com.ntleachdev.idempotent.core.GetResult;
import com.ntleachdev.idempotent.core.IdempotencyService;
import com.ntleachdev.idempotent.core.KeyFormat;
import com.ntleachdev.idempotent.core.StoredResponse;
import com.ntleachdev.idempotent.exception.IdempotencyConfigurationException;
import com.ntleachdev.idempotent.exception.IdempotencyKeyFormatException;
import com.ntleachdev.idempotent.exception.IdempotencyKeyMissingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String ATTR_LOCKED_KEY = "_idempotency_lock_acquired";
    private static final String ATTR_IDEMPOTENCY_KEY = "_idempotency_key";
    private static final String ATTR_TTL = "_idempotency_ttl_seconds";
    private static final long DEFAULT_MAX_BYTES = 1024 * 1024; // 1MB

    private final IdempotencyService idempotencyService;
    private final com.ntleachdev.idempotent.validation.KeyValidator keyValidator;

    public IdempotencyInterceptor(final IdempotencyService idempotencyService,
                                  final ObjectProvider<com.ntleachdev.idempotent.validation.KeyValidator> keyValidatorProvider) {
        this.idempotencyService = idempotencyService;
        this.keyValidator = keyValidatorProvider.getIfAvailable();
    }

    @Override
    public boolean preHandle(final @NonNull HttpServletRequest request,
                             final @NonNull HttpServletResponse response,
                             final @NonNull Object handler) throws Exception {
        if (!(handler instanceof final HandlerMethod hm)) {
            return true;
        }

        final Idempotent annotation = hm.getMethodAnnotation(Idempotent.class);
        if (annotation == null) {
            return true;
        }

        final var key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            throw new IdempotencyKeyMissingException("Missing required X-Idempotency-Key header");
        }

        if (!validateKeyFormat(key, annotation.format())) {
            throw new IdempotencyKeyFormatException("Invalid idempotency key format. Expected: " + annotation.format());
        }

        final Duration responseTtl = annotation.maxAge().scale().toDuration(annotation.maxAge().value());
        final Duration lockTtl = Duration.ofSeconds(30);

        final GetResult result = idempotencyService.getIfCachedOrAcquireLock(key, lockTtl, responseTtl);
        if (result.status() == GetResult.Status.ACQUIRED) {
            // lock acquired by us, mark request so afterCompletion stores the response
            request.setAttribute(ATTR_LOCKED_KEY, Boolean.TRUE);
            request.setAttribute(ATTR_IDEMPOTENCY_KEY, key);
            request.setAttribute(ATTR_TTL, responseTtl.getSeconds());
            return true;
        }

        // replay
        final var maybe = result.response();
        if (maybe.isPresent()) {
            final var stored = maybe.get();
            if (stored.isProcessing()) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Request with this idempotency key is currently being processed");
                return false;
            }

            logger.info("Interceptor replay: key={}", key);
            writeStoredResponseToHttp(StoredResponse.toResponseEntity(stored), response);
            return false;
        }

        // fallback - should not happen
        return true;
    }

    @Override
    public void afterCompletion(final HttpServletRequest request, @NonNull final HttpServletResponse response,
                                @NonNull final Object handler, final Exception ex) throws Exception {
        final Object locked = request.getAttribute(ATTR_LOCKED_KEY);
        if (!(locked instanceof Boolean) || !((Boolean) locked)) {
            // ?
            return;
        }

        final Object keyObj = request.getAttribute(ATTR_IDEMPOTENCY_KEY);
        final Object ttlObj = request.getAttribute(ATTR_TTL);
        if (!(keyObj instanceof final String key) || !(ttlObj instanceof Long)) {
            return;
        }

        final Duration ttl = Duration.ofSeconds((Long) ttlObj);

        if (ex != null) {
            idempotencyService.releaseLock(key);
            return;
        }

        // response should be wrapped by IdempotencyFilter as ContentCachingResponseWrapper
        if (!(response instanceof final ContentCachingResponseWrapper wrapped)) {
            logger.warn("Response was not wrapped; skipping idempotency store for key={}", key);
            idempotencyService.releaseLock(key);
            return;
        }

        final byte[] body = wrapped.getContentAsByteArray();

        if (body.length > DEFAULT_MAX_BYTES) {
            logger.warn("Captured response too large ({} bytes), skipping idempotency store for key={}", body.length, key);
            idempotencyService.releaseLock(key);
            return;
        }

        final Map<String, List<String>> headers = wrapped.getHeaderNames().stream()
                .filter(h -> !isHopByHopHeader(h))
                .collect(Collectors.toMap(h -> h, h -> new ArrayList<>(wrapped.getHeaders(h))));
        try {
            idempotencyService.markAsComplete(key, wrapped.getStatus(), headers, body, Instant.now(), ttl);
        } catch (Exception e) {
            logger.error("Failed to store idempotent response for key={}", key, e);
            idempotencyService.releaseLock(key);
        }
    }

    private boolean validateKeyFormat(final String key, final KeyFormat format) {
        return switch (format) {
            case ANY -> true;
            case UUID -> isValidUUID(key);
            case ALPHANUMERIC -> key.matches("^[a-zA-Z0-9]+$");
            case CUSTOM -> {
                if (this.keyValidator == null) {
                    throw new IdempotencyConfigurationException("KeyFormat.CUSTOM requires a KeyValidator bean to be provided");
                }
                yield this.keyValidator.isValid(key);
            }
        };
    }

    private static boolean isValidUUID(final String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    private static final Set<String> HOP_BY_HOP = Arrays.stream(new String[]{
            "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
            "TE", "Trailer", "Transfer-Encoding", "Upgrade"
    }).map(String::toLowerCase).collect(Collectors.toSet());

    private static boolean isHopByHopHeader(final String header) {
        return header != null && HOP_BY_HOP.contains(header.toLowerCase());
    }

    private void writeStoredResponseToHttp(final ResponseEntity<byte[]> entity, final HttpServletResponse response) throws IOException {
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((k, v) -> {
            if (!isHopByHopHeader(k)) {
                v.forEach(value -> response.addHeader(k, value));
            }
        });

        final var body = entity.getBody();
        if (body != null && body.length > 0) {
            response.getOutputStream().write(body);
        }
    }
}