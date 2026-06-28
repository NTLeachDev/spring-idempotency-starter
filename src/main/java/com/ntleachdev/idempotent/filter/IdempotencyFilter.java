package com.ntleachdev.idempotent.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Lightweight wrapper filter that ensures responses are captured by
 * ContentCachingResponseWrapper for requests that include an idempotency key.
 *
 * Actual idempotency decision-making is performed in IdempotencyInterceptor,
 * which has access to the resolved handler and method annotations.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class IdempotencyFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final @NonNull HttpServletResponse response,
                                    final @NonNull FilterChain filterChain) throws ServletException, IOException {
        final var key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        final ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            // Ensure body is copied back so client receives response
            wrapped.copyBodyToResponse();
        }
    }
}