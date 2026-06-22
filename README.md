# Spring Idempotency Starter

A lightweight Spring Boot starter that provides endpoint idempotency through the `@Idempotent` annotation.

The library uses Spring AOP to intercept annotated controller methods and replay previously stored responses when the same idempotency key is received.

## Features

* `@Idempotent` annotation for endpoint-level idempotency
* Storage-agnostic design
* Pluggable backend via `IdempotencyStore`
* Supports Redis, PostgreSQL, or custom implementations
* Replays the original HTTP response for duplicate requests

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
Idempotency-Key: 123e4567-e89b-12d3-a456-426614174000
```

The first request executes normally and stores the response.

Subsequent requests using the same idempotency key return the previously stored response without re-executing the controller.

## Response Requirements

All methods annotated with `@Idempotent` must return:

```java
ResponseEntity<T>
```

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

The library is storage-agnostic and relies on the `IdempotencyStore` interface.

```java
public interface IdempotencyStore {

    Optional<StoredResponse> get(String key);

    boolean tryAcquire(String key, Duration ttl);

    void saveResponse(String key, StoredResponse response);

    void release(String key);
}
```

Implementations may use:

* Redis / Valkey
* PostgreSQL
* DynamoDB
* In-memory storage
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

* Redis starter module
* PostgreSQL implementation
* Spring Boot auto-configuration
* HTTP-layer implementation using filters/interceptors
* Metrics and observability support

## License

MIT

