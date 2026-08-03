# Error Handling — RFC 7807 ProblemDetail, Consistently

**Services:** all three web services · **Key classes:** `GatewayExceptionHandler`,
`GlobalExceptionHandler` (activity + gamification), `InvalidCredentialsException`,
`ActivityNotFoundException`, `InactiveActivityException`, `InvalidTimeRangeException`

## What it is / why it's notable

Every service returns errors in one machine-readable, standardized shape — Spring's `ProblemDetail`
(the RFC 7807 `application/problem+json` model) — instead of Spring's default whitelabel error page
or ad-hoc JSON that drifts per endpoint. That consistency is the point: a client parses one error
format everywhere. Two design touches raise it above "we added an exception handler": the login
error is deliberately identical for unknown-email and wrong-password (no user enumeration), and
because the gateway is a real reverse proxy, a downstream service's `ProblemDetail` reaches the
client **byte-for-byte unchanged** — the gateway doesn't re-wrap or flatten it, so the error a
client sees is the error the owning service actually produced.

## How it works

```mermaid
flowchart TB
    A[Controller / service throws] --> B{"@RestControllerAdvice
    @ExceptionHandler matches?"}
    B -->|"ActivityNotFoundException"| C["404 ProblemDetail (activity-service)"]
    B -->|"NoSuchElementException"| D["404 ProblemDetail (gamification-service)"]
    B -->|"InactiveActivityException"| I["409 'inactive, re-enable it' (activity-service)"]
    B -->|"MethodArgumentNotValidException<br/>InvalidTimeRangeException"| J["400 field violations joined"]
    B -->|"HttpMessageNotReadableException"| E["400 'Invalid request body'"]
    B -->|"InvalidCredentialsException"| F["401 'Invalid email or password' (gateway)"]
    B -->|no match| G["Spring default (falls through)"]
    C & D & E & F & I & J --> H["reverse-proxied through gateway UNCHANGED"]
```

### Each service advises on the exceptions it actually throws

**Gateway — one handler, and a security-conscious message:**
```java
@RestControllerAdvice
public class GatewayExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }
}
```
The exception itself hardcodes one message for both failure modes:
```java
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("Invalid email or password"); }
}
```
`AuthService.login` throws it whether the email doesn't exist *or* the password is wrong (see
[Authentication & Identity Propagation](authentication-and-identity.md)) — so the response never
reveals which accounts exist. This is a small thing that's easy to get wrong by returning "user not
found" vs "bad password."

**activity-service — four handlers, three status codes:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ActivityNotFoundException.class)
    public ProblemDetail handleNotFound(ActivityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler(InactiveActivityException.class)
    public ProblemDetail handleInactiveActivity(InactiveActivityException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
    @ExceptionHandler(InvalidTimeRangeException.class)
    public ProblemDetail handleTimeout(InvalidTimeRangeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) { ... }
}
```
`ActivityNotFoundException` carries a specific message (`"Activity not found: {name}"` /
`"Activity log not found: {id}"`) thrown from every lookup path.

The status-code choices are the interesting part. A **soft-deleted activity is a `409 Conflict`,
not a `404`** — the activity genuinely exists, the client just can't log against it, and conflating
the two would tell a client "create this activity" when the right move is "re-enable it." The
exception message says exactly that:
```java
super("Activity '" + activityName + "' is inactive and cannot accept new log entries. "
        + "Re-enable the activity before logging time against it.");
```
It's thrown in `mapToActivityLog`, **before** any XP, bonus roll, streak update, or outbox row is
produced (issue #7) — so a rejected log leaves no partial side effects behind.

**Declarative validation.** `MethodArgumentNotValidException` is what `@Valid @RequestBody` throws,
and the handler flattens every field violation into one detail string rather than returning only the
first:
```java
String detail = ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("; "));
```
The constraints live on the request records — e.g. `ActivityLogRequest` requires a non-blank
`activityName`, a non-null `startTime`/`endTime`, and `@PastOrPresent` on `startTime`. That last
one previously read `@FutureOrPresent`, which had the rule exactly backwards: you log time you have
already spent, so a start time in the future is the invalid case.

**gamification-service — 404 + a validation 400:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidRequestBody(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
    }
}
```
`LevelTrackerRequestDTO`'s `xp` field carries a `@PositiveOrZero` constraint, and
`LevelTrackerController` validates the body with `@Valid` — but gamification-service's
`GlobalExceptionHandler` above has no `@ExceptionHandler(MethodArgumentNotValidException.class)`.
A negative `xp` therefore never reaches this service's own `ProblemDetail` handling at all: the
validation failure falls straight through to Spring's own default error handling instead, unlike
activity-service (which does have that handler — see above). The DTO's leading comment block
preserves an older, now-dead approach — a compact constructor that threw `IllegalArgumentException`
on `xp < 0` during deserialization — commented out in favor of the declarative constraint; don't
mistake the comment for live behavior.

### The errors Spring Security writes itself

The `401` for a missing/expired/malformed bearer token and the `403` for a caller without the
required role never reach a `@RestControllerAdvice` — they are written by the filter chain, before
any controller exists. This resource server has not customized either: Spring's defaults
(`BearerTokenAuthenticationEntryPoint` / the default `AccessDeniedHandler`) apply, which send an
**empty body** and put the failure reason in a `WWW-Authenticate` header rather than a
`ProblemDetail`. That's valid OAuth 2.0 behavior, but it means these two status codes are the one
place a client parses a different error shape than everywhere else in this API — a gap worth
closing by registering a custom `AuthenticationEntryPoint`/`AccessDeniedHandler` on
`oauth2ResourceServer(...)`, not yet done.

### The response shape

```json
{
  "type": "about:blank",
  "status": 404,
  "detail": "Activity not found: Study",
  "instance": "/activity/Study"
}
```

### Pass-through through the gateway

Because routing is a genuine reverse proxy (see [API Gateway Routing](api-gateway-routing.md)), the
`instance` field of a downstream error still shows the *downstream* service's own path (e.g.
`/level/999999`), not the gateway's `/api/level/999999` — proof the gateway forwards the response
untouched rather than re-serializing it. `GET /api/activity/does-not-exist` and
`GET /api/level/999999` each return the exact body their owning service produces directly.

## Known edges (honest inventory)

- Only the exceptions above are advised; anything else falls through to Spring's defaults — there's
  no catch-all `@ExceptionHandler(Exception.class)`.
- Bean Validation is wired in **activity-service only** — its `MethodArgumentNotValidException`
  handler is the one shown above. gamification-service's DTOs carry constraints
  (`@Valid`/`@PositiveOrZero` on `LevelTrackerRequestDTO`, for one) but its `GlobalExceptionHandler`
  has no handler for the exception those constraints throw, so violations don't get this service's
  `ProblemDetail` treatment. api-gateway's `LoginRequest`/`RegisterRequest` carry constraints too
  (`@Email`, `@NotBlank`, `@NotNull` on `RegisterRequest.role`) but the controller has no `@Valid`
  and the service has no validation starter on its classpath at all — those annotations are
  currently inert. Note if that ever gets wired up: `RegisterRequest.role` is `@NotNull` while
  `AuthService.register` explicitly defaults a null role to `Role.USER` — turning validation on
  without removing that annotation would start rejecting the ordinary "register with no role"
  request that default exists to support.

## Try it

```bash
curl -i http://localhost:8080/api/activity/DoesNotExist -H "Authorization: Bearer $TOKEN"   # 404 ProblemDetail
curl -i -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"nobody@example.com","password":"x"}'                                         # 401 "Invalid email or password"
curl -i -X POST http://localhost:8080/api/level -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"activityId":1,"xp":-5}'
# -> 400, but NOT this service's ProblemDetail shape — @Valid's MethodArgumentNotValidException
#    has no handler in gamification-service's GlobalExceptionHandler, so it falls through to
#    Spring's own default error handling (see "Known edges" above)

# Soft-delete an activity, then try to log against it -> 409, no XP or streak side effects
curl -i -X POST http://localhost:8080/api/activitylog -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"activityName":"Retired","startTime":"2026-07-29T09:00:00","endTime":"2026-07-29T09:30:00"}'

# Field violations are joined into one detail string
curl -i -X POST http://localhost:8080/api/activitylog -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"activityName":"","startTime":null,"endTime":null}'
# -> 400 "Activity name is required; Start time is required; End time is required"
```

## Related
[Authentication & Identity Propagation](authentication-and-identity.md) (the no-enumeration login) ·
[API Gateway Routing](api-gateway-routing.md) (byte-for-byte pass-through)
