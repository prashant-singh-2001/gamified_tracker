# API Gateway

**Public entry point — JWT authentication/authorization and declarative routing to the backend services.** · Port **8080**

The only service a client should talk to. It handles user registration/login (issuing JWTs), authenticates every non-public request, enforces role-based access and per-caller identity, and routes calls to the Activity Service and Gamification Service.

## Role in the system

```
   client
     │  Bearer <JWT>
     ▼
  api-gateway (8080)                  Spring Cloud Gateway (Server MVC)
     │  Spring Security + JWT           declarative routes, not hand-proxied
     ├──lb://activity-service────────►  activity-service (8081)
     └──lb://gamification-service────►  gamification-service (8082)
     │
     ▼
  PostgreSQL (5433)  ← user_entity (auth)
```

This is a **real Spring Cloud Gateway** (`spring-cloud-starter-gateway-server-webmvc`, servlet-based so it shares the same Spring Security stack as auth) — routes are declared in `application.yaml` and resolved via Eureka + Spring Cloud LoadBalancer (`lb://...`). There are no `ActivityClient`/`GamificationClient` Feign clients and no per-endpoint proxy controllers in this service anymore; both were removed when routing was migrated to be declarative. Registers with **Eureka**; owns a small **PostgreSQL** table for users.

## Responsibilities

- Register and authenticate users; issue signed JWTs carrying the user's `role` **and** numeric `userId`.
- Validate the JWT on every non-public request, populate the security context, and inject a trusted `userId` request header downstream (overwriting any client-supplied one — see Security model).
- Enforce authorization — admin-only routes are gated at the URL level in `SecurityConfig`.
- Route activity, activity-log, level-tracker, and threshold requests to the appropriate backend service via declarative Gateway rules.

## Tech stack

- Java 17, Spring Boot 3.5, Spring Cloud 2025 (Eureka client, **Gateway Server MVC**, LoadBalancer)
- **Spring Security 6** + **JWT** (`io.jsonwebtoken:jjwt` 0.13), `BCryptPasswordEncoder`
- Spring Data JPA + PostgreSQL (users only)
- **Bucket4j + Redis** (Lettuce) — Gateway rate limiting (see [Rate limiting](#rate-limiting))
- `spring-boot-starter-actuator` (health/info)
- Entry point: `ApiGatewayApplication` (`@SpringBootApplication`)

## Security model (the centerpiece)

- **`JwtUtil.generateToken(email, role, userId)`** — signs an HS256 token carrying `role` and the numeric `userId` (the user's `User.id`) as claims, plus a configurable expiry (`jwt.expiration`). `validateToken` parses and returns the claims. `AuthService` calls this with `user.getId()` on both register and login.
- **`JwtFilter`** — on each request to a protected path, validates the `Bearer` token, requires the `userId` claim to be present (rejects with `401` if it's missing — e.g. a token minted before this claim existed), and sets an `Authentication` whose authority comes from the token's `role` claim (`ROLE_USER` / `ROLE_ADMIN`). It then **wraps the request** to force a `userId` HTTP header to the JWT's trusted value — overriding `getHeader`, `getHeaders`, *and* `getHeaderNames` (not just `getHeader`, which the Gateway's own request-forwarding doesn't consult) so a client-forged `userId` header is fully replaced, not merely shadowed, before the request is routed downstream. `shouldNotFilter` exempts `/auth/**`, swagger, `/v3/api-docs`, `/swagger-resources`, `/webjars`, and `/actuator/**`.
- **`SecurityConfig`** — `@EnableWebSecurity`. `/auth/**`, swagger/OpenAPI paths, and `/actuator/**` are `permitAll`; `POST /api/activity` (and its trailing-slash form) is gated with `.hasRole("ADMIN")`; everything else requires authentication. `JwtFilter` runs before `UsernamePasswordAuthenticationFilter`. There is no `@PreAuthorize`/`@EnableMethodSecurity` in this service — admin gating happens at the URL/route level because routing itself is now declarative, so there's no controller method left to annotate.
- **Why the header matters**: `activity-service` and `gamification-service` have no security of their own — they trust whatever arrives in the `userId` header on `POST /activitylog/` and `POST /level`. This filter is what makes that trust well-founded when the request comes through the Gateway (or via activity-service's internal Feign call, which forwards the same header explicitly). See [API.md § Authentication](../API.md#authentication) for the full write-vs-read trust model, including which reads are intentionally open across users.

> ⚠️ `POST /auth/register` honors the requested `role`, so anyone can self-register as `ADMIN`. Acceptable for this demo; not production-safe.

## API reference

JSON bodies. `/auth/**` and `/actuator/**` are public; all `/api/**` paths require `Authorization: Bearer <token>`.

### Auth — `/auth`

#### `POST /auth/register`
Create a user and return a JWT.

Request:
| Field | Type | Notes |
|-------|------|-------|
| `firstName`, `lastName` | String | |
| `email` | String | unique |
| `password` | String | BCrypt-hashed before storage |
| `role` | enum | `USER` \| `ADMIN` — optional, defaults to `USER`; honored as-is (see warning above) |

Response `200 OK`: a raw JWT string (not JSON-wrapped), carrying `role` and `userId` claims.

#### `POST /auth/login`
Authenticate and return a JWT. Request: `{ "email", "password" }`.
- `200 OK` → raw JWT string
- `401 Unauthorized` → `ProblemDetail` (`"Invalid email or password"`) — same message whether the email is unknown or the password is wrong (no user enumeration).

### Activity (routed) — `/api/activity`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/activity` | authenticated | list all activities |
| `GET` | `/api/activity/{name}` | authenticated | one activity by name (`404` if missing) |
| `POST` | `/api/activity` | **ADMIN** | create an activity (`403` for non-admins) |

Request/response bodies mirror the Activity Service (`name`, `category`, `xpMultiplier`, `active`, `description`, `createdAt`) — see [API.md](../API.md) and the [activity-service README](../activity-service/README.md).

### Activity Log (routed) — `/api/activitylog`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/activitylog/{id}` | authenticated | one log by id — open read, any user's log |
| `POST` | `/api/activitylog` | authenticated | record a session (computes XP + bonus roll, notifies gamification). Always writes as the caller — `userId` comes from the trusted header, not the body |
| `GET` | `/api/activitylog/user/{id}` | authenticated | all logs for a user — open read, `{id}` can be anyone |

`POST` response includes `bonusApplied`, `bonusMultiplier`, and `leveledUp` — populated for real only on this response; `GET` endpoints always return them as `false`/`1.0`/`false`. Full field list in [API.md](../API.md).

### Level Tracker (routed) — `/api/level`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/level` | authenticated | list every row — open read |
| `GET` | `/api/level/{id}` | authenticated | one row by internal id (`404` if missing) — open read |
| `POST` | `/api/level` | authenticated | create-or-update XP for **the caller's own** activity. Normally called internally by the Activity Service, not directly by clients |
| `GET` | `/api/level/user/{userId}` | authenticated | all rows for a user — open read, `{userId}` can be anyone |
| `GET` | `/api/level/activity/{activityId}` | authenticated | all rows for an activity |

`POST` body has no `userId` field — see [gamification-service README](../gamification-service/README.md).

### Activity Level Threshold (routed) — `/api/threshold`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/threshold` | authenticated | list all thresholds |
| `POST` | `/api/threshold/activity` | authenticated | look up one threshold by composite key (a read, despite `POST`) |
| `POST` | `/api/threshold` | authenticated | create/overwrite a threshold — not admin-gated |

### Misc

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/hello` | authenticated | returns `"Hello {email}"` — diagnostic |

## Data model

**`user_entity`** (unique on `email`):
| Column | Type | Notes |
|--------|------|-------|
| `id` | bigint | PK — this is the numeric `userId` embedded in every JWT |
| `first_name` / `last_name` | String | |
| `email` | String | unique |
| `password` | String | BCrypt hash |
| `role` | enum (STRING) | `USER` \| `ADMIN` |

## Configuration

| Var / key | Default | Purpose |
|-----------|---------|---------|
| `JWT_SECRET` | (committed dev fallback) | HS256 signing secret — override in prod |
| `JWT_EXPIRATION` | `86400000` (24h, ms) | token lifetime — read via `@Value("${jwt.expiration}")` |
| `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` | postgres defaults | users DB |
| `server.port` | `8080` | HTTP port |
| `eureka.client.service-url.defaultZone` | `http://eureka-server:8761/eureka` | registry |
| `management.endpoints.web.exposure.include` | `health,info` | actuator endpoints exposed, `permitAll`'d in `SecurityConfig` |

See root [`.env.example`](../.env.example).

## Gateway routes

Defined in the **Java DSL** — `config/RouteConfiguration.java` as `RouterFunction` beans — **not** in
`application.yaml`. Routing moved out of YAML so the Bucket4j `rateLimit()` filter could be attached
(its key resolver is Java-DSL-only); the old declarative `spring.cloud.gateway.server.webmvc.routes`
block is commented out in `application.yaml` to avoid registering duplicate, un-throttled routes.

| Route id | Predicate | Target | Filters |
|---|---|---|---|
| `activity` | `/api/activity/**`, `/api/activitylog/**` | `lb://activity-service` | Two mutually-exclusive `rewritePath(...)` regexes (not `stripPrefix`) — activity-service's list/create endpoints are mapped at a bare `/`, and Spring 6's `PathPatternParser` no longer treats `/activity` and `/activity/` as equivalent, so the base path needs its trailing slash rewritten in explicitly + `rateLimit` |
| `gamification` | `/api/level/**`, `/api/threshold/**`, `/api/notifications/**` | `lb://gamification-service` | `stripPrefix(1)` + `rateLimit` |

## Rate limiting

Redis-backed request throttling via the Server MVC gateway's **Bucket4j** `rateLimit()` filter
(`config/RateLimitConfig.java` wires a Lettuce `AsyncProxyManager<String>` over Redis; the two
proxied routes carry the filter). See [`docs/features/rate-limiting.md`](../docs/features/rate-limiting.md) for the full design.

- **Key:** the trusted `userId` header (injected by `JwtFilter`), falling back to client IP —
  `config/RateLimitKeyResolver.byUserIdOrIp()`. One user hitting their limit never throttles another.
- **Limits (token bucket, tunable via `RL_*` env / `rate-limit.*` in `application.yaml`):**
  100 req/min per proxied route; 10 req/min on `/auth/**`.
- **`/auth/**`** are local controllers, *not* proxied routes, so the gateway filter can't see them —
  they're guarded separately by `config/AuthRateLimitFilter` (a servlet filter, keyed on IP).
- **Over the limit → `429 Too Many Requests`** with an `X-RateLimit-Remaining` header (the `/auth`
  guard returns `{"error":"Too many requests"}`).
- **Infra:** `redis:7-alpine` in `docker-compose.yml` (gateway `depends_on` it, `service_healthy`).

**Try it** — the Postman collection has a **Rate Limiting** folder that hammers a proxied route
(as one authenticated user) until it trips `429` (run it via Collection Runner). Or by hand against
a proxied route with a token:
```bash
TOKEN=...   # from POST /auth/register
for i in $(seq 1 130); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/level \
    -H "Authorization: Bearer $TOKEN"
done | sort | uniq -c        # ~100 × 200, then 429 (default bucket = 100 / 60s, per user)
```

## Inter-service dependencies

- **Routes to:** activity-service and gamification-service, declaratively via `lb://` (Eureka + LoadBalancer) — no Feign clients remain in this service.
- **Called by:** external clients.
- **Infra:** Eureka, PostgreSQL.

## Running

```bash
docker-compose up --build          # whole stack, from repo root
# or standalone (needs Postgres + Eureka; activity/gamification for routed calls):
cd api-gateway && mvn spring-boot:run
```

### Quick auth walkthrough
```bash
# 1. register (returns a token)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Ada","lastName":"L","email":"ada@example.com","password":"secret","role":"USER"}')

# 2. call a protected, routed endpoint
curl http://localhost:8080/api/activity -H "Authorization: Bearer $TOKEN"
```

A ready-to-import Postman collection covering every endpoint (including a dedicated IDOR-verification folder) lives at [../postman/](../postman/).

## Testing

```bash
mvn test          # from api-gateway/
```

Includes `@WebMvcTest` controller tests and auth tests.

> ⚠️ `ApiGatewayApplicationTests` (full `@SpringBootTest` context load) needs the `postgres` host and only resolves inside the docker-compose network — it fails under a bare local `mvn test`. This is a pre-existing test-config gap, not a code defect.

## Troubleshooting

- **`401` on every `/api/**` call** — missing/expired/invalid `Bearer` token, or a token minted before the `userId` claim existed; re-login.
- **`403` on `POST /api/activity`** — the token's role is `USER`, not `ADMIN`. Register/login as an admin.
- **`400` on a downstream `POST` (`/api/activitylog`, `/api/level`) when hit directly, bypassing the Gateway** — `userId` is a required header on those two endpoints; the Gateway supplies it automatically, direct calls to `:8081`/`:8082` must supply it manually (see [API.md](../API.md)).
- **Health check:** `curl http://localhost:8080/actuator/health` — no token required (`permitAll`'d in both `SecurityConfig` and `JwtFilter`).

## Related docs

- [Root README](../README.md) · [API.md](../API.md) · [activity-service README](../activity-service/README.md) · [gamification-service README](../gamification-service/README.md)
