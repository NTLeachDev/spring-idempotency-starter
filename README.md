# Spring Idempotency Starter

Lightweight starter to add idempotency to HTTP endpoints via the `@Idempotent` annotation.

This project is a POC-quality library intended for Spring applications. It provides a storage-agnostic idempotency
mechanism with lock semantics and response replay. It targets unary HTTP endpoints (controller methods returning
ResponseEntity).

## Key features
- Simple `@Idempotent` annotation for endpoints
- Pluggable `IdempotencyStore` implementations (in-memory, Redis, SQL, etc.)
- Processing sentinel (lock) with configurable TTL and stored-response TTL
- Pluggable key validation for `KeyFormat.CUSTOM`
- Reasonable defaults for POC use

# Getting started

## Gradle

```groovy
implementation("com.github.ntleachdev:spring-idempotency-starter:<version>")
```

## Usage

Annotate your controller method with `@Idempotent`. The client must send an idempotency key as a header
(default header name: `X-Idempotency-Key`).

Example controller:

```java
@RestController
public class PaymentController {

    @Idempotent
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> createPayment() {
        PaymentResponse response = paymentService.createPayment();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

# Key validation
- Built-in formats are available (ANY, UUID, ALPHANUMERIC, etc.).
- For `KeyFormat.CUSTOM` you must provide a `KeyValidator` bean:

```java
@Bean
public KeyValidator myValidator() {
    return key -> key != null && key.matches("[0-9a-fA-F\\-]{36}");
}
```

# Important constraints
- `@Idempotent` endpoints should return `ResponseEntity<?>` so the library can capture the full response.
- Streaming endpoints are unsupported; this library targets unary request/response flows.

##  Storage API

Implement `IdempotencyStore` to plug a backend. The store uses atomic lock acquisition and replacement semantics.

### Core types (overview)
- `StoredResponse` — transport-friendly stored value (status, headers/metadata, body bytes, expiry, state)
- `GetResult` — returned from lookup: either `ACQUIRED` (caller should proceed) or `REPLAY` with a
  `StoredResponse` (either PROCESSING or STORED).

### Error mapping
- Missing/invalid keys -> HTTP 400
- Operation in progress -> HTTP 409 (Conflict)

### Examples and extensions
- In-memory store is provided for POC/testing. For production use, implement a Redis or SQL-backed
  `IdempotencyStore` using atomic SET NX / conditional update semantics.

# Contributing
- Please open issues or PRs. Tests and documentation improvements are welcome.

License
MIT

