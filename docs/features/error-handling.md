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

**api-gateway — the no-enumeration 401 above, plus the same validation 400.** Its DTOs had carried
`@Email`/`@NotBlank` constraints for a long time, but the service had no `spring-boot-starter-validation`
on the classpath and no `@Valid` on the controller — so there was no validator to run them and the
annotations were dead. All three parts (starter, `@Valid`, handler) are now present, which is what
makes them enforceable. One constraint was deleted rather than enforced: `RegisterRequest.role` was
annotated `@NotNull` while `AuthService.register` explicitly defaults a null role to `Role.USER` —
turning validation on with that annotation intact would have started rejecting ordinary
registrations. The service's default is the real rule, so the annotation went.

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
The `400` path is subtle: `LevelTrackerRequestDTO`'s compact constructor throws on `xp < 0` **during
JSON deserialization**, which Spring surfaces as `HttpMessageNotReadableException` — so a negative-XP
body is rejected with a clean `400` before it ever reaches the service, rather than blowing up as an
unhandled `500`.

### Even the errors Spring Security writes itself

The `401` for a missing/expired/malformed bearer token and the `403` for a caller without the
required role never reach a `@RestControllerAdvice` — they are written by the filter chain, before
any controller exists. Spring's defaults (`BearerTokenAuthenticationEntryPoint` /
`BearerTokenAccessDeniedHandler`) send an **empty body** and put the reason in a
`WWW-Authenticate` header. That is valid OAuth 2.0, but it would mean a client needs a second error
format for exactly two status codes. `ProblemDetailAuthenticationHandler` is registered as both the
entry point and the access-denied handler, so those two get RFC 7807 bodies like everything else —
while still emitting `WWW-Authenticate`, so spec-compliant clients keep working:

```java
.oauth2ResourceServer(oauth2 -> oauth2
        .jwt(...)
        .authenticationEntryPoint(problemDetailAuthenticationHandler)
        .accessDeniedHandler(problemDetailAuthenticationHandler))
```
The `401` detail is deliberately generic — `"Authentication required or token is invalid"`. The
underlying exception can distinguish "expired" from "bad signature" from "malformed", but echoing
that back tells an attacker which half of a forged token to fix. Same instinct as the login
message above.

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
- The negative-XP check on `LevelTrackerRequestDTO` is enforced twice over: once by the compact
  constructor (during deserialization, surfacing as `HttpMessageNotReadableException`) and once by
  `@PositiveOrZero`. The two produce different messages for the same bad input depending on which
  fires first. Harmless, but it is duplication worth collapsing.

## Try it

```bash
curl -i http://localhost:8080/api/activity/DoesNotExist -H "Authorization: Bearer $TOKEN"   # 404 ProblemDetail
curl -i -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"nobody@example.com","password":"x"}'                                         # 401 "Invalid email or password"
curl -i -X POST http://localhost:8080/api/level -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"activityId":1,"xp":-5}'                           # 400 "Invalid request body"

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
