package com.ntleachdev.idempotent.aspect;

import com.ntleachdev.idempotent.annotation.Idempotent;
import com.ntleachdev.idempotent.core.IdempotencyService;
import com.ntleachdev.idempotent.core.KeyFormat;
import com.ntleachdev.idempotent.exception.IdempotencyConfigurationException;
import com.ntleachdev.idempotent.exception.IdempotencyKeyFormatException;
import com.ntleachdev.idempotent.exception.IdempotencyKeyMissingException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

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
    public Object injectIdempotency(final ProceedingJoinPoint joinPoint) throws Throwable {
        verifyResponseEntity(joinPoint);
        final var idempotentAnnotation = getIdempotentAnnotation(joinPoint);
        final var idempotencyKey = getIdempotencyKey(idempotentAnnotation.format());

        final var result = idempotencyService.getIfCachedOrAcquireLock(
                idempotencyKey,
                idempotentAnnotation.maxAge().scale().toDuration(idempotentAnnotation.maxAge().value())
        );

        if (result.isPresent()) {
            logger.info("Found stored response for key: {}", idempotencyKey);
            return result.get();
        }

        return proceed(joinPoint, idempotencyKey, idempotentAnnotation);
    }

     private Object proceed(final ProceedingJoinPoint joinPoint, final String idempotencyKey,
                             final Idempotent idempotentAnnotation) throws Throwable {
         try {
             final var result = joinPoint.proceed();
             idempotencyService.markAsComplete(idempotencyKey, (ResponseEntity<?>) result, idempotentAnnotation.maxAge());
             return result;
         } catch (final Throwable throwable) {
             logger.error("Exception processing request with key: {}", idempotencyKey, throwable);
             idempotencyService.releaseLock(idempotencyKey);
             throw throwable;
         }
     }

    private void verifyResponseEntity(final JoinPoint joinPoint) {
        final var method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        if (!ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
            throw new IdempotencyConfigurationException("Idempotent methods must return ResponseEntity");
        }
    }

    private String getIdempotencyKey(final KeyFormat keyFormat) {
        final var idempotencyKey = getKeyFromRequest();
        if (!validateKeyFormat(idempotencyKey, keyFormat)) {
            throw new IdempotencyKeyFormatException("Invalid idempotency key format. Expected: " + keyFormat);
        }

        return idempotencyKey;
    }

    private static Idempotent getIdempotentAnnotation(final JoinPoint joinPoint) {
        final var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return method.getAnnotation(Idempotent.class);
    }

    private static String getKeyFromRequest() {
        final var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IdempotencyKeyMissingException("No servlet request context");
        }

        final var request = attributes.getRequest();
        final var idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyMissingException("Missing required X-Idempotency-Key header");
        }
        return idempotencyKey;
    }

    private static boolean validateKeyFormat(final String key, final KeyFormat format) {
        return switch (format) {
            case ANY -> true;
            case UUID -> isValidUUID(key);
            case ALPHANUMERIC -> key.matches("^[a-zA-Z0-9]+$");
            case CUSTOM -> true; // Implement custom logic as needed
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
}
