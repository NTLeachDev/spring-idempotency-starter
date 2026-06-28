package com.ntleachdev.idempotent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class IdempotencyExceptionHandler {

    @ExceptionHandler(IdempotencyKeyMissingException.class)
    public ResponseEntity<String> handleMissingKey(IdempotencyKeyMissingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyFormatException.class)
    public ResponseEntity<String> handleFormat(IdempotencyKeyFormatException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IdempotentOperationInProgressException.class)
    public ResponseEntity<String> handleInProgress(IdempotentOperationInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConfigurationException.class)
    public ResponseEntity<String> handleConfig(IdempotencyConfigurationException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}

