# TODO / Fix Backlog — Gamified Tracker

Analysis of enhancements, engineering improvements, and business-logic gaps, grounded in the actual codebase. Items are tagged 🔴 high / 🟡 medium / 🟢 nice-to-have and grouped by category.

> Priority order to tackle first is at the bottom ([Top 5](#top-5-to-do-first)).

---

## Business Logic Improvements

Real correctness / trust gaps in how the app behaves.

- [x] ~~🔴 **Derive `userId` from the token; enforce ownership (IDOR).**~~ ✅ **Fixed.**
  The JWT now carries the numeric `userId` claim (set at register/login). `api-gateway`'s `JwtFilter` injects it as a trusted `userId` request header, overwriting/stripping any client-supplied value, before the request is routed downstream; `activity-service` forwards the same header on its internal Feign call. `POST /api/activitylog` and `POST /api/level` derive the acting user from that header — their request DTOs no longer accept `userId` in the body at all. **Ownership was deliberately *not* enforced on reads** — `GET .../{id}` and `GET .../user/{id}` remain open across users by design (a social/leaderboard feature), a scope decision made explicitly during the fix, not an oversight. See [API.md § Authentication](API.md#authentication).

- [x] ~~🔴 **Save the activity log *before* the cross-service gamification call.**~~ ✅ **Fixed** (issue [#4](https://github.com/prashant-singh-2001/gamified_tracker/issues/4), via issue [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16)'s event-driven decoupling).
  `ActivityLogServiceImpl.addActivityLogResponseResponseEntity` is now `@Transactional`: it saves the `ActivityLog` **first**, then in the same transaction writes an `outbox_event` row (Transactional Outbox pattern) — the synchronous Feign call to gamification is gone entirely, replaced by an async RabbitMQ publish. See [`EVENT_DRIVEN_DECOUPLING.md`](EVENT_DRIVEN_DECOUPLING.md).

- [x] ~~🔴 **Fix the lost-update race on XP accumulation.**~~ ✅ **Fixed** (issue #5, [PR #29](https://github.com/prashant-singh-2001/gamified_tracker/pull/29)).
  `LevelTrackerServiceImpl.save()` now does an atomic `insertIfAbsent` (native `INSERT ... ON CONFLICT DO NOTHING`) followed by `SELECT ... FOR UPDATE` pessimistic row locking for the rest of the transaction, plus a DB unique constraint on `(user_id, activity_id)`. Verified with a live 20-request concurrent burst — exact `totalXp`, a single row, coherent archive sequence. See [gamification-service README](gamification-service/README.md#key-internal-flow--leveltrackerserviceimplsave).

- [ ] 🟡 **Reject backwards time ranges (currently a 500).**
  `endTime < startTime` → negative `durationMinutes` → negative `xpEarned` → the `LevelTrackerRequestDTO` compact constructor (`xp < 0`) throws inside activity-service → unhandled 500. Validate `endTime > startTime` up front and return a clean 400.
  - File: `activity-service/src/main/java/com/tracker/activity/service/ActivityLogService.java`

- [ ] 🟡 **Enforce the `Activity.active` soft-delete flag.**
  The flag exists but nothing checks it — you can log time against a disabled activity.
  - Files: `activity-service/.../dao/Activity.java`, `.../service/ActivityLogService.java`

- [ ] 🟡 **Provide a default level-progression curve.**
  Levels depend entirely on manually `POST`ed `ActivityLevelThreshold` rows; with none seeded, every user is stuck at level 1. Add a default formula (e.g. `xpRequired = base * level^1.5`) or seed data.
  - Files: `gamification-service/.../service/LevelTrackerService.java`, `.../ActivityLevelThresholdService.java`

- [ ] 🟡 **Surface "XP to next level" / progress.**
  Responses expose `level` and `currentLevelXp` but not `xpForNextLevel` / progress % — the most motivating number in a gamified tracker.
  - File: `gamification-service/.../dto/LevelTrackerDto.java` (+ service)

- [ ] 🟢 **Resolve the two competing multipliers.**
  `xpEarned` uses stored `Activity.xpMultiplier`; `Category.baseXpMultiplier()` is defined but **never called**. Pick a source of truth (per-activity override with category default is a clean model).
  - Files: `activity-service/.../dao/Category.java`, `.../service/ActivityLogService.java`

- [x] ~~🟢 **Make the bonus roll visible; stop discarding the level-up event.**~~ ✅ **Fixed.**
  `ActivityLogResponse` gained `bonusApplied`/`bonusMultiplier`/`leveledUp`; `LevelTrackerDto` gained `leveledUp`. **Caveat carried forward from the fix**: these three fields are computed in-memory at write time, not persisted — every `GET` endpoint that returns either DTO hardcodes them to `false`/`1.0`/`false` regardless of the row's real history. Only the specific `POST` response that created the data reflects real values. See [API.md § Known Issues Summary](API.md#known-issues-summary).

---

## Enhancements (new capabilities)

- [ ] 🟡 **Streaks** — highest-leverage gamification feature, currently absent. Needs `lastActivityDate` per user/activity + consecutive-day logic; pairs with the existing bonus multiplier.
- [ ] 🟡 **Achievements / badges** and **leaderboards** — the unused `LevelTrackerRepository.getTotalXpByUserId` query is already the basis for cross-activity totals / ranking.
- [ ] 🟡 **Level-up notifications / events** — surface the `LeveledUp` outcome instead of discarding it.
- [x] ~~🟡 **springdoc-openapi (Swagger UI)**~~ ✅ **Done.** All three application services (gateway, activity, gamification) expose live Swagger UI (`/swagger-ui.html`) and OpenAPI JSON (`/v3/api-docs`) — see [API.md § Interactive API Docs](API.md#interactive-api-docs-swagger). `API.md` itself is still hand-maintained, so it can still drift from the generated spec; the DTO-duplication problem below is unaffected.
- [x] ~~🟢 **Event-driven decoupling (Kafka/RabbitMQ)** for activity→gamification~~ ✅ **Done** (issue [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16), closes [#4](https://github.com/prashant-singh-2001/gamified_tracker/issues/4)). Transactional Outbox → RabbitMQ → Idempotent Consumer, with a DLQ for poison messages. `leveledUp` is now eventual on the write response. See [`EVENT_DRIVEN_DECOUPLING.md`](EVENT_DRIVEN_DECOUPLING.md).
- [ ] 🟢 **Analytics endpoints** — XP over time, per-category summaries, weekly reports.

---

## Places of Improvement (engineering quality / operability)

- [x] ~~🔴 **Add a real test suite.**~~ **Largely done.** All three application services now have `@WebMvcTest` controller tests, `@DataJpaTest` repository tests, and Mockito service tests (XP math, level thresholds, the IDOR-fix header wiring, auth, and — since #16 — the outbox producer/relay and idempotent-consumer messaging classes). Remaining gap: no Testcontainers/end-to-end suite exercising the real Gateway → activity-service → RabbitMQ → gamification-service flow together — everything today is per-service with mocks at the boundary (Mockito for `RabbitTemplate`/repositories, no embedded broker).

- [x] ~~🔴 **Wire up health/observability (currently inert).**~~ ✅ **Partially done.** `spring-boot-starter-actuator` is now a dependency on all four services, exposing `health` + `info` (with liveness/readiness probe groups enabled). Each Dockerfile has a real `HEALTHCHECK` against it, and `docker-compose.yml` gates every service's startup on `depends_on: condition: service_healthy` (postgres → eureka-server → gateway → activity → gamification) — see [API.md § Health Checks](API.md#health-checks-actuator). **Still open:** Micrometer + Prometheus metrics (`/actuator/metrics` isn't exposed) and distributed tracing — there's still no way to trace one request across gateway→activity→gamification.

- [ ] 🟡 **Add bean validation.**
  DTOs only have ad-hoc compact-constructor checks. Add `jakarta.validation` (`@Valid`, `@NotNull`, `@Positive`, cross-field time-range checks) with a `MethodArgumentNotValidException` → `ProblemDetail` handler.

- [ ] 🟡 **Schema management & indexing.**
  `ddl-auto: update` everywhere is risky beyond dev — move to Flyway/Liquibase. Add indexes on hot query columns (`user_id`, `activity_id`). The shared `tracker_db` across all three services couples them (breaks service-DB independence).

- [x] ~~🟡 **Add Feign resilience.**~~ **Moot** — activity-service's Feign call to gamification-service no longer exists (issue #16 replaced it with async RabbitMQ messaging). A slow/failing gamification-service no longer fails the activity-log write at all; the outbox absorbs it and the DLQ handles poison messages instead of a circuit breaker.

- [ ] 🟡 **Remove DTO/event triplication.**
  `LevelTrackerDto`, `ActivityResponse`, etc. are copy-pasted across all three services and hand-synced — the exact drift that caused the original `getActivity`/`getAllActivities` bug. Issue #16 added one more instance of this: `ActivityLoggedEvent` is now duplicated verbatim in both activity-service and gamification-service's `messaging/` packages. Use a shared contract module or OpenAPI-generated clients.

- [ ] 🟢 **Harden the Dockerfiles.**
  All four run as **root**, use full **JDK** (not JRE/distroless), have **no `HEALTHCHECK`**, no layered-jar caching (`COPY . .` + full rebuild busts the cache on any change), and a copy-paste `EXPOSE 8080` even in services on 8081/8082. Add a non-root user, JRE base, and dependency layering.

- [ ] 🟢 **Security hardening.**
  Default JWT secret still committed as a fallback in `api-gateway/.../application.yaml`; no token refresh/revocation, no HTTPS, no CORS (blocks a future frontend). ~~no rate limiting~~ ✅ **rate limiting done** — Redis-backed Bucket4j at the Gateway (per-user key, IP fallback; 100 req/min per proxied route, 10 req/min on `/auth/**`; `429` + `X-RateLimit-Remaining`). See [`RATE_LIMITING.md`](RATE_LIMITING.md). `JwtFilter` hand-rolls auth and writes 401 directly — consider migrating to `spring-boot-starter-oauth2-resource-server`.

---

## Top 5 to do first

~~1. 🔴 Derive `userId` from the token + enforce ownership~~ — ✅ done (writes only; reads intentionally left open).
~~2. 🔴 Optimistic locking / unique constraint on `LevelTracker`~~ — ✅ done (PR #29).
~~3. 🔴 A real test suite~~ — largely done; Testcontainers end-to-end still missing.
~~4. 🟡 Actuator~~ — ✅ done (`health`/`info` + Docker healthchecks + compose startup gating); bean validation still open.

~~5. 🔴 Save the activity log before calling gamification + add Feign resilience~~ — ✅ done (issue #16: Transactional Outbox + RabbitMQ replaced the Feign call entirely; Feign resilience is moot).
~~6. 🔴 Fix the pre-existing `POST /activitylog/` 500~~ — ✅ done (issue #16 swapped `RandomGenerator.getDefault()` for `ThreadLocalRandom.current()` while rewriting that method).

Current top priorities, re-ranked:

1. 🟡 **Bean validation** (`@Valid`/`jakarta.validation`) — input correctness is still mostly ad-hoc compact-constructor checks.
2. 🟡 **Provide a default level-progression curve** — every activity is still stuck at level 1 until thresholds are seeded manually.
3. 🟢 **Metrics + tracing** — `/actuator/health` exists now; `/actuator/metrics` and cross-service tracing still don't. Tracing the outbox → RabbitMQ → consumer hop into a single trace (issue #19) is now the more interesting version of this, since #16 added an async hop to instrument.
4. 🟢 **DTO/event triplication** — `ActivityLoggedEvent` joins the existing hand-duplicated DTOs across services; a shared contract module would fix both at once.
5. 🟢 **Testcontainers end-to-end suite** — now spans Gateway → activity-service → RabbitMQ → gamification-service, not just a Feign contract.
