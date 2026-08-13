# Feature Docs — Gamified Tracker

Twenty standalone deep-dives into the notable engineering work in this codebase — each one covers
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
| [Session Integrity](session-integrity.md) | A from-scratch Iglewicz-Hoaglin outlier detector with three fallback tiers, an absolute threshold and daily cap closing a self-consistency gap the statistics alone couldn't catch, and a quarantine-not-reject admin review workflow |

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
| [Achievement Badges](achievement-badges.md) | A criteria-driven rules engine (one `switch`, four badge kinds, all data-defined) reusing the codebase's idempotent-upsert grant pattern — and an honest gap: implemented, tested, not yet wired to a trigger |
| [Streaks](streaks.md) | A consecutive-day gap-state-machine multiplier that stacks onto XP, entirely inside the producing service — the consuming service needed zero changes |
| [Analytics](analytics.md) | In-memory stream aggregation over raw activity logs (category summaries, a zero-filled daily XP timeline, a single-query weekly report) — deliberately sidesteps this repo's H2/Postgres SQL-portability trap |
| [AI Weekly Coaching Digest](ai-weekly-digest.md) | A provider-agnostic `ChatModel` narrator over numbers computed entirely in Java, interchangeable between a local Ollama container and Docker Model Runner with zero code change, always-200 with a `narrativeStatus` field so "off" and "the model failed" never need a second response shape |

## Cross-Cutting & Quality

| Doc | What it demonstrates |
|---|---|
| [Error Handling](error-handling.md) | One RFC 7807 `ProblemDetail` contract across every service, no-user-enumeration login errors, and byte-for-byte pass-through through the gateway — plus an honest gap: Spring Security's own 401/403 aren't in that shape yet |
| [Fuzzy Activity-Name Matching](fuzzy-activity-matching.md) | A hand-written Jaro-Winkler matcher scoring name + description + category, split behind a provider seam for a future embedding/LLM scorer, with two rails on automatic substitution — a high threshold and an ambiguity guard — because the XP it awards is irreversible |
| [Testing Strategy](testing-strategy.md) | A sliced test pyramid (48 classes) with `InOrder`/`ArgumentCaptor` side-effect verification and a serialized-shape wire-contract guard |

## Platform

| Doc | What it demonstrates |
|---|---|
| [Service Discovery, Health Orchestration & Containerization](observability-and-discovery.md) | Eureka + Actuator health checks driving Docker Compose's dependency-ordered startup, layered multi-stage non-root Docker builds, and per-service Swagger |
| [Distributed Tracing & Metrics](distributed-tracing.md) | Zipkin + Prometheus + Grafana across all services — one trace follows a request through the RabbitMQ hop, all via config and auto-instrumentation |
| [Config Server](config-server.md) | A Spring Cloud Config Server that runs, serves configuration correctly, and is honestly documented as Phase 1 — no service imports it yet |

## Feature → service → key class

| Feature | Service(s) | Entry point to read first |
|---|---|---|
| Auth & identity propagation | api-gateway | `security/SecurityConfig.java`, `security/UserIdHeaderFilter.java` |
| Rate limiting | api-gateway | `config/RateLimitConfig.java` |
| Gateway routing | api-gateway | `config/RouteConfiguration.java` |
| Session integrity | activity-service | `domain/DurationOutlierDetector.java`, `service/DurationOutlierEvaluationService.java` |
| Event-driven decoupling | activity-service, gamification-service, contracts | `service/impl/ActivityLogServiceImpl.java`, `contracts/.../event/ActivityLoggedEvent.java` |
| Concurrency-safe XP | gamification-service | `service/impl/LevelTrackerServiceImpl.java` |
| Leveling engine | activity-service, gamification-service | `dao/Activity.java`, `domain/LevelOutcome.java`, `domain/LevelCurve.java` |
| Level-up notifications | gamification-service | `service/impl/NotificationServiceImpl.java` |
| Rank & level system | gamification-service | `service/impl/RankRecomputeServiceImpl.java` |
| Achievement badges | gamification-service | `service/impl/AchievementServiceImpl.java` |
| Streaks | activity-service | `service/impl/ActivityLogServiceImpl.java` (`applyStreak`) |
| Analytics | activity-service | `service/impl/AnalyticsServiceImpl.java` |
| AI weekly coaching digest | activity-service | `service/impl/InsightsServiceImpl.java`, `domain/WeeklyDigestNarrator.java`, `domain/ChatModelWeeklyDigestNarrator.java` |
| Fuzzy activity-name matching | activity-service | `domain/ActivityMatcher.java`, `domain/ActivityNameScorer.java`, `service/ActivityNameResolutionService.java` |
| Error handling | all three web services | `exception/GlobalExceptionHandler.java` |
| Testing strategy | all six modules | `*/src/test/...` |
| Discovery, health & containers | all four services | `docker-compose.yml`, `*/Dockerfile` |
| Distributed tracing & metrics | all four services | `docker-compose.yml`, `prometheus.yml`, `grafana/` |
| Config server (Phase 1) | config-service | `ConfigServiceApplication.java`, `config-repo/*.yaml` |

## Module map

Six Maven modules under the root reactor, plus a coverage aggregator:

| Module | Kind | Port |
|---|---|---|
| `api-gateway` | Spring Cloud Gateway (Server MVC) — auth, rate limiting, routing | 8080 |
| `activity-service` | Activities, activity logs, streaks, analytics, outbox producer | 8081 |
| `gamification-service` | XP, levels, ranks, badges, notifications, event consumer | 8082 |
| `eureka-server` | Service discovery | 8761 |
| `config-service` | Spring Cloud Config Server — Phase 1, not yet consumed by any service | 8888 |
| `contracts` | Library, not a service — cross-service wire contracts | — |
| `coverage-report` | Build-only module: jacoco `report-aggregate` across the above | — |
