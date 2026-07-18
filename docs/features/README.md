# Feature Docs — Gamified Tracker

Eleven standalone deep-dives into the notable engineering work in this codebase — each one covers
what the feature is, why it's worth a second look, how it actually works (with a diagram and the
load-bearing code), its config, and a way to try it live. Verified against the current source at
time of writing; if a snippet looks stale, trust the code and treat the doc as a map, not the
territory.

## Security & Edge

| Doc | What it demonstrates |
|---|---|
| [Authentication & Identity Propagation](authentication-and-identity.md) | JWT issuance + the IDOR fix: why overriding `getHeader()` alone wasn't enough, and what closes it |
| [Rate Limiting](rate-limiting.md) | Redis-backed Bucket4j on the Server MVC gateway (not the reactive `RequestRateLimiter`) — two independent throttles for two different reasons |
| [API Gateway Routing](api-gateway-routing.md) | Java-DSL declarative routing, `lb://` load balancing, and why routes moved out of YAML |

## Event-Driven Core

| Doc | What it demonstrates |
|---|---|
| [Event-Driven Decoupling](event-driven-decoupling.md) | Transactional Outbox → Polling Publisher → Idempotent Consumer + DLQ, spanning two services — the project's headline architecture feature |

## Gamification Engine

| Doc | What it demonstrates |
|---|---|
| [Concurrency-Safe XP Accumulation](concurrency-safe-xp.md) | Atomic upsert + pessimistic row lock + unique constraint closing a real lost-update race, plus an append-only audit trail |
| [Leveling Engine](leveling-engine.md) | Override-with-default XP multiplier resolution (closed a latent 0-XP bug) + a sealed-interface level outcome with exhaustive pattern matching |
| [Level-Up Notifications](level-up-notifications.md) | A caller-scoped notification feed, plus a real JPA-attribute-naming bug and how it was fixed |

## Cross-Cutting & Quality

| Doc | What it demonstrates |
|---|---|
| [Error Handling](error-handling.md) | One RFC 7807 `ProblemDetail` contract across every service, no-user-enumeration login errors, and byte-for-byte pass-through through the gateway |
| [Testing Strategy](testing-strategy.md) | A sliced test pyramid (27 classes) with `InOrder`/`ArgumentCaptor` side-effect verification and `@MockBean`-hermetic context tests |

## Platform

| Doc | What it demonstrates |
|---|---|
| [Service Discovery, Health Orchestration & Containerization](observability-and-discovery.md) | Eureka + Actuator health checks driving Docker Compose's dependency-ordered startup, layered multi-stage non-root Docker builds, and per-service Swagger |
| [Distributed Tracing & Metrics](distributed-tracing.md) | Zipkin + Prometheus + Grafana across all services — one trace follows a request through the RabbitMQ hop, all via config and auto-instrumentation |

## Feature → service → key class

| Feature | Service(s) | Entry point to read first |
|---|---|---|
| Auth & identity propagation | api-gateway | `security/JwtFilter.java` |
| Rate limiting | api-gateway | `config/RateLimitConfig.java` |
| Gateway routing | api-gateway | `config/RouteConfiguration.java` |
| Event-driven decoupling | activity-service, gamification-service | `service/impl/ActivityLogServiceImpl.java` |
| Concurrency-safe XP | gamification-service | `service/impl/LevelTrackerServiceImpl.java` |
| Leveling engine | activity-service, gamification-service | `dao/Activity.java`, `domain/LevelOutcome.java` |
| Level-up notifications | gamification-service | `service/impl/NotificationServiceImpl.java` |
| Error handling | all three web services | `exception/GlobalExceptionHandler.java` |
| Testing strategy | all four | `*/src/test/...` |
| Discovery, health & containers | all four | `docker-compose.yml`, `*/Dockerfile` |
| Distributed tracing & metrics | all four | `docker-compose.yml`, `prometheus.yml`, `grafana/` |

## Related
[Root README](../../README.md) · [API.md](../../API.md)
