# TODO / Fix Backlog — Gamified Tracker

Analysis of enhancements, engineering improvements, and business-logic gaps, grounded in the actual codebase (not a generic Spring checklist — see `CLAUDE.md` for that). Items are tagged 🔴 high / 🟡 medium / 🟢 nice-to-have and grouped by category.

> Priority order to tackle first is at the bottom ([Top 5](#top-5-to-do-first)).

---

## Business Logic Improvements

Real correctness / trust gaps in how the app behaves.

- [ ] 🔴 **Derive `userId` from the token; enforce ownership (IDOR).**
  The JWT identifies the caller by *email*, but the domain keys on an unrelated client-supplied `Long userId` (`AddActivityLogRequest`, `GET /api/activitylog/user/{id}`, `GET /level/user/{userId}`). Any authenticated user can log activities as, and read the XP/levels of, **any** other user. Resolve the authenticated principal → user id server-side (in the gateway) and enforce ownership on reads. Also add a real mapping between the gateway `User.id` and the `Long userId` used downstream.
  - Files: `api-gateway/.../controller/ActivityLogGatewayController.java`, `.../ActivityGatewayController.java`, `activity-service/.../dto/AddActivityLogRequest.java`, `activity-service/.../service/ActivityLogService.java`

- [ ] 🔴 **Save the activity log *before* the cross-service gamification call.**
  In `ActivityLogService.addActivityLogResponseResponseEntity()` the Feign call `gamificationClient.createLevelTracker(...)` runs *before* `activityLogRepository.save(...)`. If gamification is down/rejects, the log is never persisted and the user gets an error. Persist locally first; treat the XP update as a secondary, resilient step (ideally async — see Enhancements).
  - File: `activity-service/src/main/java/com/tracker/activity/service/ActivityLogService.java` (~lines 39–41)

- [ ] 🔴 **Fix the lost-update race on XP accumulation.**
  `LevelTrackerService.save()` does read-modify-write (`existing.setTotalXp(existing.getTotalXp() + dto.xp())`) with no locking. Concurrent logs for the same user+activity silently drop an increment. `LevelTracker` has **no `@Version`** and **no unique constraint on `(userId, activityId)`**, so concurrent first-time writes can also create duplicate rows. Fix with optimistic locking (`@Version`) + a DB unique constraint, or an atomic `UPDATE ... SET total_xp = total_xp + ?`.
  - Files: `gamification-service/.../service/LevelTrackerService.java` (~lines 61–76), `gamification-service/.../dao/LevelTracker.java`

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

- [ ] 🟢 **Make the bonus roll visible; stop discarding the level-up event.**
  The RandomGenerator bonus is applied silently (no `bonusApplied` flag), and the sealed `LevelOutcome.LeveledUp` is computed then thrown away. Return a `leveledUp: true` / bonus breakdown so clients can react.
  - Files: `activity-service/.../service/ActivityLogService.java`, `gamification-service/.../service/LevelTrackerService.java`

---

## Enhancements (new capabilities)

- [ ] 🟡 **Streaks** — highest-leverage gamification feature, currently absent. Needs `lastActivityDate` per user/activity + consecutive-day logic; pairs with the existing bonus multiplier.
- [ ] 🟡 **Achievements / badges** and **leaderboards** — the unused `LevelTrackerRepository.getTotalXpByUserId` query is already the basis for cross-activity totals / ranking.
- [ ] 🟡 **Level-up notifications / events** — surface the `LeveledUp` outcome instead of discarding it.
- [ ] 🟡 **springdoc-openapi (Swagger UI)** — zero live API docs today; `API.md` is hand-maintained and has already drifted. Generated OpenAPI keeps docs honest and enables typed client generation (kills the DTO-duplication problem below).
- [ ] 🟢 **Event-driven decoupling (Kafka/RabbitMQ)** for activity→gamification — also solves the "save order / outage loses data" issue.
- [ ] 🟢 **Analytics endpoints** — XP over time, per-category summaries, weekly reports.

---

## Places of Improvement (engineering quality / operability)

- [ ] 🔴 **Add a real test suite.**
  All four `*ApplicationTests` are just `contextLoads()`. The XP math, level thresholds, auth/authorization, and Feign contracts are entirely uncovered — which is why every change has needed manual verification. Add: unit tests for service math, `@WebMvcTest` for controllers + `@PreAuthorize`, `@DataJpaTest` for the custom `@Query`s, Testcontainers for end-to-end.

- [ ] 🔴 **Wire up health/observability (currently inert).**
  Every `application.yaml` configures `management.endpoints...health`, but **no module depends on `spring-boot-starter-actuator`**, so `/actuator/health` 404s — docker-compose has no real healthchecks and Eureka can't health-check properly. Add actuator (liveness/readiness), Micrometer + Prometheus, and distributed tracing (Micrometer Tracing/Zipkin) — there's currently no way to trace a request across gateway→activity→gamification.

- [ ] 🟡 **Add bean validation.**
  DTOs only have ad-hoc compact-constructor checks. Add `jakarta.validation` (`@Valid`, `@NotNull`, `@Positive`, cross-field time-range checks) with a `MethodArgumentNotValidException` → `ProblemDetail` handler.

- [ ] 🟡 **Schema management & indexing.**
  `ddl-auto: update` everywhere is risky beyond dev — move to Flyway/Liquibase. Add indexes on hot query columns (`user_id`, `activity_id`). The shared `tracker_db` across all three services couples them (breaks service-DB independence).

- [ ] 🟡 **Add Feign resilience.**
  No timeouts, retries, circuit breaker, or fallbacks (no Resilience4j). A slow/failing gamification service fails every activity log. Also, the gateway's `GatewayExceptionHandler` only maps `FeignException.NotFound` — a downstream 400/503 becomes a generic 500 at the gateway.

- [ ] 🟡 **Remove DTO triplication.**
  `LevelTrackerDto`, `ActivityResponse`, etc. are copy-pasted across all three services and hand-synced — the exact drift that caused the original `getActivity`/`getAllActivities` bug. Use a shared contract module or OpenAPI-generated clients.

- [ ] 🟢 **Harden the Dockerfiles.**
  All four run as **root**, use full **JDK** (not JRE/distroless), have **no `HEALTHCHECK`**, no layered-jar caching (`COPY . .` + full rebuild busts the cache on any change), and a copy-paste `EXPOSE 8080` even in services on 8081/8082. Add a non-root user, JRE base, and dependency layering.

- [ ] 🟢 **Security hardening.**
  Default JWT secret still committed as a fallback in `api-gateway/.../application.yaml`; no token refresh/revocation, no HTTPS, no CORS (blocks a future frontend), no rate limiting. `JwtFilter` hand-rolls auth and writes 401 directly — consider migrating to `spring-boot-starter-oauth2-resource-server`.

---

## Top 5 to do first

1. 🔴 **Derive `userId` from the token + enforce ownership** — fixes the IDOR, the biggest real risk.
2. 🔴 **Save the activity log before calling gamification + add Feign resilience** — stop losing data on outages.
3. 🔴 **Optimistic locking + `(userId, activityId)` unique constraint on `LevelTracker`** — fix the lost-update race.
4. 🔴 **A real test suite** — the XP/level/auth logic is completely uncovered.
5. 🟡 **Actuator + bean validation** — operability + input correctness, small effort, high payoff.
