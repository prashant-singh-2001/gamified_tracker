# Feature Docs — Gamified Tracker

Fourteen standalone deep-dives into the notable engineering work in this codebase — each one covers
what the feature is, why it's worth a second look, how it actually works (with a diagram and the
load-bearing code), its config, and a way to try it live. Verified against the current source at
time of writing; if a snippet looks stale, trust the code and treat the doc as a map, not the
territory.

## Security & Edge

| Doc | What it demonstrates |
|---|---|
| [Authentication & Identity Propagation](authentication-and-identity.md) | JWT issuance, validation delegated to Spring Security's OAuth2 resource server, and the IDOR fix: why overriding `getHeader()` alone wasn't enough, and what closes it |
| [Rate Limiting](rate-limiting.md) | Redis-backed Bucket4j on the Server MVC gateway (not the reactive `RequestRateLimiter`) — two independent throttles for two different reasons |
| [API Gateway Routing](api-gateway-routing.md) | Java-DSL declarative routing, `lb://` load balancing, and why routes moved out of YAML |

## Event-Driven Core

| Doc | What it demonstrates |
|---|---|
| [Event-Driven Decoupling](event-driven-decoupling.md) | Transactional Outbox → Polling Publisher → Idempotent Consumer + DLQ, spanning two services, over a wire contract defined once in a shared `contracts` module — the project's headline architecture feature |

## Gamification Engine

| Doc | What it demonstrates |
|---|---|
| [Concurrency-Safe XP Accumulation](concurrency-safe-xp.md) | Atomic upsert + pessimistic row lock + unique constraint closing a real lost-update race, plus an append-only audit trail |
| [Leveling Engine](leveling-engine.md) | Override-with-default XP multiplier resolution (closed a latent 0-XP bug), a sealed-interface level outcome with exhaustive pattern matching, and a formula-derived default progression curve that explicit per-activity thresholds always override |
| [Level-Up Notifications](level-up-notifications.md) | A caller-scoped notification feed, plus a real JPA-attribute-naming bug and how it was fixed |

## Progression, Ranks & Retention

| Doc | What it demonstrates |
|---|---|
| [Rank & Level System](rank-and-level-system.md) | A scheduled batch computing percentile-based tiers into a materialized snapshot, so every read stays O(1) instead of re-ranking on every request |
| [Achievement Badges](achievement-badges.md) | A criteria-driven rules engine (one `switch`, four badge kinds, all data-defined) reusing the codebase's idempotent-upsert grant pattern, evaluated inside the XP transaction so both callers of `save` are covered by one line |
| [Streaks](streaks.md) | A consecutive-day gap-state-machine multiplier that stacks onto XP, entirely inside the producing service — the consuming service needed zero changes |

## Cross-Cutting & Quality

| Doc | What it demonstrates |
|---|---|
| [Error Handling](error-handling.md) | One RFC 7807 `ProblemDetail` contract across every service — including the 401/403 Spring Security writes itself — no-user-enumeration login errors, and byte-for-byte pass-through through the gateway |
| [Testing Strategy](testing-strategy.md) | A sliced test pyramid (47 classes, 217 tests) with `InOrder`/`ArgumentCaptor` side-effect verification, a serialized-shape wire-contract guard, and `@MockitoBean`-hermetic context tests |

## Platform

| Doc | What it demonstrates |
|---|---|
| [Service Discovery, Health Orchestration & Containerization](observability-and-discovery.md) | Eureka + Actuator health checks driving Docker Compose's dependency-ordered startup, layered multi-stage non-root Docker builds, and per-service Swagger |
| [Distributed Tracing & Metrics](distributed-tracing.md) | Zipkin + Prometheus + Grafana across all services — one trace follows a request through the RabbitMQ hop, all via config and auto-instrumentation |

## Feature → service → key class

| Feature | Service(s) | Entry point to read first |
|---|---|---|
| Auth & identity propagation | api-gateway | `security/SecurityConfig.java`, `security/UserIdHeaderFilter.java` |
| Rate limiting | api-gateway | `config/RateLimitConfig.java` |
| Gateway routing | api-gateway | `config/RouteConfiguration.java` |
| Event-driven decoupling | activity-service, gamification-service, contracts | `service/impl/ActivityLogServiceImpl.java`, `contracts/.../event/ActivityLoggedEvent.java` |
| Concurrency-safe XP | gamification-service | `service/impl/LevelTrackerServiceImpl.java` |
| Leveling engine | activity-service, gamification-service | `dao/Activity.java`, `domain/LevelOutcome.java`, `domain/LevelCurve.java` |
| Level-up notifications | gamification-service | `service/impl/NotificationServiceImpl.java` |
| Rank & level system | gamification-service | `service/impl/RankRecomputeServiceImpl.java` |
| Achievement badges | gamification-service | `service/impl/AchievementServiceImpl.java` |
| Streaks | activity-service | `service/impl/ActivityLogServiceImpl.java` (`applyStreak`) |
| Error handling | all three web services | `exception/GlobalExceptionHandler.java` |
| Testing strategy | all five modules | `*/src/test/...` |
| Discovery, health & containers | all four services | `docker-compose.yml`, `*/Dockerfile` |
| Distributed tracing & metrics | all four services | `docker-compose.yml`, `prometheus.yml`, `grafana/` |

## Module map

Five Maven modules under the root reactor, plus a coverage aggregator:

| Module | Kind | Port |
|---|---|---|
| `api-gateway` | Spring Cloud Gateway (Server MVC) — auth, rate limiting, routing | 8080 |
| `activity-service` | Activities, activity logs, streaks, outbox producer | 8081 |
| `gamification-service` | XP, levels, ranks, badges, notifications, event consumer | 8082 |
| `eureka-server` | Service discovery | 8761 |
| `contracts` | Library, not a service — cross-service wire contracts | — |
| `coverage-report` | Build-only module: jacoco `report-aggregate` across the above | — |
