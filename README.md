# Spring Idempotency Starter

A lightweight Spring Boot starter that provides endpoint idempotency through the `@Idempotent` annotation.

The library uses Spring AOP to intercept annotated controller methods and replay previously stored responses when the same idempotency key is received.

## Features

* `@Idempotent` annotation for endpoint-level idempotency
* Storage-agnostic design with pluggable backends (Redis, PostgreSQL, DynamoDB, etc.)
* Concurrent request handling with distributed lock semantics
* Replays original HTTP response for duplicate requests
* Configurable idempotency key validation (UUID, alphanumeric, custom)
* Automatic response serialization/deserialization

## Installation

### Gradle

```groovy
implementation("com.github.ntleachdev:spring-idempotency-starter:<version>")
```

### Maven

```xml
<dependency>
    <groupId>com.github.ntleachdev</groupId>
    <artifactId>spring-idempotency-starter</artifactId>
    <version>${version}</version>
</dependency>
```

## Usage

Annotate a controller endpoint with `@Idempotent`.

```java
@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Idempotent
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment() {

        PaymentResponse response = paymentService.createPayment();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
```

Clients must provide an idempotency key.

```http
POST /payments
X-Idempotency-Key: 123e4567-e89b-12d3-a456-426614174000
```

The first request executes normally and stores the response.

Subsequent requests using the same idempotency key return the previously stored response without re-executing the controller.

## Response Requirements

`@Idempotent` endpoints must:
- Return `ResponseEntity<?>`
- Not use `@ResponseStatus`
- Not write directly to `HttpServletResponse`

This constraint allows the library to reliably capture and replay complete HTTP responses while keeping the implementation simple.

Example:

```java
@Idempotent
@PostMapping
public ResponseEntity<CreateOrderResponse> createOrder() {
    ...
}
```

Unsupported:

```java
@Idempotent
@PostMapping
public CreateOrderResponse createOrder() {
    ...
}
```

## Storage

The library uses the `IdempotencyStore` interface for storage abstraction:

```java
public interface IdempotencyStore {
    Optional<StoredResponse> get(String key);
    boolean tryAcquire(String key, Duration ttl);
    Optional<StoredResponse> getIfCachedOrAcquireLock(String key, Duration ttl);
    void storeResponse(String key, StoredResponse response, Duration ttl);
    void releaseLock(String key);
}
```

Implementations can use:
* Redis / Valkey
* PostgreSQL
* DynamoDB
* In-memory storage (for testing/POC)
* Any custom backend

## Stored Response

Responses are stored in a transport-friendly format:

```java
public record StoredResponse(
    int statusCode,
    Map<String, List<String>> headers,
    byte[] body,
    Instant createdAt
) {}
```

## How It Works

```text
Request
  ↓
@Idempotent Aspect
  ↓
Check existing response
  ↓
Acquire lock
  ↓
Execute controller
  ↓
Store response
  ↓
Return response
```

For duplicate requests:

```text
Request
  ↓
@Idempotent Aspect
  ↓
Existing response found
  ↓
Replay stored response
```

## Roadmap

* Spring Boot auto-configuration module
* Redis starter implementation
* PostgreSQL starter implementation
* Metrics (cache hit rate, lock contention)
* Distributed tracing support

## License

MIT

