package com.ntleachdev.idempotent.aspect;

import com.ntleachdev.idempotent.core.IdempotencyService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
public class IdempotencyAspect {
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final IdempotencyService idempotencyService;

    @Autowired
    public IdempotencyAspect(final IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Around("@annotation(com.ntleachdev.idempotent.annotation.Idempotent)")
    public Object injectIdempotency(final ProceedingJoinPoint joinPoint) {
        final var returnType = getReturnType(joinPoint);
        logger.info("Method return type: {}", returnType);

        final var idempotencyKey = getIdempotencyKey();

        final var storedResponse = idempotencyService.getStoredResponse(idempotencyKey, returnType);
        if (storedResponse != null) {
            logger.info("Found stored response: {}", storedResponse);
            return storedResponse;
        }

        final var isFirstRequest = idempotencyService.tryProcessingRequest(idempotencyKey);
        if (!isFirstRequest) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is currently being processed. Please retry shortly.");
        }

        return proceed(joinPoint, idempotencyKey);
    }

    private Object proceed(final ProceedingJoinPoint joinPoint, final String idempotencyKey) {
        try {
            final var result = joinPoint.proceed();
            idempotencyService.markAsComplete(idempotencyKey, result);
            return result;
        } catch (final Throwable throwable) {
            logger.error("Error processing request", throwable);
            idempotencyService.evictLock(idempotencyKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "An error occurred while processing the request. Please retry.");
        }
    }

    private ResolvableType getReturnType(final JoinPoint joinPoint) {
        final var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        logger.info("Processing idempotent method: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());

        return ResolvableType.forMethodReturnType(method);
    }

    private String getIdempotencyKey() {
        final var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        final var request = attributes.getRequest();
        final var idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required X-Idempotency-Key header");
        }

        return idempotencyKey;
    }
}
