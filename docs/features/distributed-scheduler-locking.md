# Distributed Scheduler Locking — ShedLock on the two `@Scheduled` jobs

**Services:** `activity-service`, `gamification-service` · **Key classes:**
`OutboxRelay`, `RankRecomputeServiceImpl`, `ProcessedEvent`/`ProcessedEventRepository`,
`ActivityLoggedListener`, `SchedulerLockConfig` (both services)

## What it is / why it's notable

Both services run a `@Scheduled` job with no coordination between instances:
`OutboxRelay.publishPending()` polls the outbox every 2 seconds, and
`RankRecomputeServiceImpl.recompute()` rebuilds the leaderboard snapshot every 5 minutes. Neither had
any locking (issue #82). With one replica of each service — today's actual deployment, and
`docker-compose.yml` structurally can't run more (`container_name` + fixed host ports) — this was
inert. It stops being inert the moment anyone adds a second instance or sets listener concurrency
above 1: every instance polls the same rows and every instance publishes them.

Fixing only that would have been incomplete. Investigating it surfaced a second, independent bug
already living in production, one the issue's own description assumed away:

> the consumer's `processed_event` guard "eventually prevents duplicate XP awards" — so relay
> duplicates are just wasted work, not double-awarded XP.

That's not true. `ProcessedEvent` has a manually-assigned `String @Id`, no `@GeneratedValue`, no
`@Version`. Spring Data's default `isNew()` is `id == null` — always `false` for this entity — so
`processedEventRepository.save(...)` compiled to `entityManager.merge()`, not `persist()`. A racing
duplicate delivery doesn't throw a constraint violation; `merge()` finds the existing row by its own
`SELECT` and issues a silent `UPDATE`. The listener's own comment ("THIS save throws") described
behavior the code never actually had. Fixing the relay's locking alone would have made duplicate
*publishes* rarer while leaving the layer meant to catch the ones that still got through completely
inert. Both had to be fixed together.

## How it works

### 1. `@SchedulerLock` on both jobs

```java
// OutboxRelay.java
@Scheduled(fixedDelayString = "${outbox.relay.delay-ms:2000}")
@SchedulerLock(name = "outboxRelay_publishPending", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
@Transactional
public void publishPending() { ... }

// RankRecomputeServiceImpl.java
@Scheduled(fixedDelayString = "${ranking.recompute-interval-ms:300000}")
@SchedulerLock(name = "rankRecompute_recompute", lockAtMostFor = "PT4M", lockAtLeastFor = "PT5S")
public Integer recompute() { ... }
```
`lockAtMostFor` is a ceiling above the worst realistic run time, so a crashed instance's lock still
expires and the next tick isn't wedged forever — it is *not* how long the lock is expected to be
held in the normal case. `lockAtLeastFor` closes the opposite gap: two instances with close-but-not-
identical clocks both waking for the same tick.

**`recompute()` had to change its return type from `int` to `Integer`.** ShedLock's method-proxy
interceptor wraps the return value to represent "lock not acquired, this invocation was skipped" —
and it has no value it can substitute for a primitive `int`. Discovered by actually booting the
context in a test, not by reading the docs: the first version of this fix shipped with `int` still
in place, and `GamificationServiceApplicationTests` logged
`LockingNotSupportedException: Can not lock method returning primitive value` on every scheduled
tick — the job would never have run at all in production. `RankRecomputeService.recompute()`'s
callers (`RankController`, the test suite) need no changes: `int`/`Integer` widen and unbox across
every call site that assigns or compares the result.

Because `@SchedulerLock` is a Spring AOP interceptor on the bean method itself, it also wraps the
on-demand `POST /ranks/recompute` path (`RankController` calls the same `recompute()`) — not just
the `@Scheduled` invocation. That's the right behavior here: serializing an admin-triggered manual
recompute against a concurrently-running scheduled one is correct, not a gap.

### 2. Making the consumer guard actually throw

```java
// ProcessedEvent.java
public class ProcessedEvent implements Persistable<String> {
    @Id private String idempotencyKey;
    private LocalDateTime processedAt;

    @Override @Transient public String getId() { return idempotencyKey; }
    @Override @Transient public boolean isNew() { return true; }   // append-only table: every row IS new
}
```
```java
// ActivityLoggedListener.java
processedEventRepository.saveAndFlush(new ProcessedEvent(key, LocalDateTime.now()));
```
`isNew()` hardcoded `true` forces Spring Data to call `persist()` (a real `INSERT`) instead of
`merge()`. `saveAndFlush` (not `save`) makes that `INSERT` hit the database at this exact line rather
than at end-of-transaction flush — the ordering the class's own comment already claimed, now actually
structural instead of an accident of an unrelated repository's `flushAutomatically` elsewhere. A
genuine race now throws `DataIntegrityViolationException` here, rolls back the whole transaction (XP
never applied), and redelivery finds `existsById` already `true`.

### 3. The lock table itself needs a schema-qualified name

```java
// SchedulerLockConfig.java, one per service
@Bean
public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                    .withJdbcTemplate(new JdbcTemplate(dataSource))
                    .usingDbTime()
                    .withTableName("activity.shedlock")   // "gamification.shedlock" in the other service
                    .build());
}
```
Every other piece of JPA code in this repo is schema-qualified for free, via Hibernate's
`hibernate.default_schema` property — but `JdbcTemplateLockProvider` runs plain SQL through a raw
`JdbcTemplate`, entirely outside Hibernate. Without an explicit schema prefix it would resolve
`shedlock` against the connection's own default `search_path` (`public`), not the schema the
migration below actually creates it in.

### 4. The missing migration

`processed_event` had **zero** Flyway migration anywhere in the repo despite gamification-service
running `ddl-auto: validate` — pure schema drift; `validate` checks that tables and columns exist but
never checks primary keys, so a hand-created table without one would start cleanly and leave the
whole guard silently inert. `V4__create_processed_event.sql` adds it defensively (`IF NOT EXISTS`,
plus a `DO $$ ... $$` block that adds the primary key only if genuinely absent — this has to be a
no-op against any database where the table already exists correctly). `V5__create_shedlock.sql`
(both services) adds the standard ShedLock table.

## The test wrinkle

Both application classes carry `@EnableScheduling`, so `@SpringBootTest` boots the scheduler and
these jobs fire during the plain context tests — and both services' H2 test properties set
`spring.sql.init.mode=never`, so there is no `shedlock` table for a real `JdbcTemplateLockProvider`
to use. Fix: `@MockBean LockProvider` in `ActivityServiceApplicationTests` and
`GamificationServiceApplicationTests`. Mockito returns `Optional.empty()` from the unstubbed
lock-acquisition call, which ShedLock reads as "not acquired" — the scheduled tick is silently
skipped instead of failing on a missing table. Same hermetic-infra-mocking pattern this repo already
uses for Redis/Bucket4j in `SecurityRulesTest`.

No unit or `@SpringBootTest` can prove genuine cross-instance mutual exclusion in-process — that
needs two real JVMs against one database. `OutboxRelayTest` and `RankRecomputeServiceImplTest` each
assert (via reflection) that the method still carries `@SchedulerLock` with a non-blank `name`, so a
future refactor can't silently drop it, but that is a regression guard, not proof of exclusion.

## Config

No new YAML keys — `lockAtMostFor`/`lockAtLeastFor` are literal duration strings on the annotation,
matching the convention of scattered `@Value` config elsewhere in this repo rather than the
`RateLimitProperties`-style typed block.

## Try it

```bash
docker compose up -d postgres rabbitmq eureka-server
mvn spring-boot:run -pl activity-service -Dspring-boot.run.arguments=--server.port=8081 &
mvn spring-boot:run -pl activity-service -Dspring-boot.run.arguments=--server.port=8091 &
# log ~20 activities against either port, then:
psql -h localhost -p 5433 -U postgres -d tracker_db \
  -c "SELECT name, locked_by FROM activity.shedlock;"        # one row, one holder at a time
psql -h localhost -p 5433 -U postgres -d tracker_db \
  -c "SELECT COUNT(*) FROM gamification.processed_event;"    # equals number of logs, not double
```
Kill the instance currently holding the lock mid-tick; the other instance picks up the work once
`lockAtMostFor` expires.

## Known simplifications

- **This serializes the relay, on purpose.** At ≤100 rows per 2-second tick that ceiling is
  irrelevant; if the outbox ever genuinely backs up, the fix is a claim-column lease that lets
  disjoint batches run in parallel, not a bigger lock.
- **This fixes the producer and the consumer guard, not deployability.**
  `docker-compose.yml` still cannot run two instances of any service (`container_name` + fixed host
  ports) — nothing in this change makes the race *observable* without the manual two-instance setup
  above.
- **Left out on purpose, filed separately rather than smuggled in here:** no index on
  `outbox_event.published_at` (the hot poll is a growing full scan), no purge of published rows, a
  poison row retried forever with no attempt counter, `convertAndSend` stamping `publishedAt` without
  a publisher confirm, and the DLQ (`gamification.activity-logged.dlq`) having no consumer or
  alerting at all.

## Related
[Event-Driven Decoupling](event-driven-decoupling.md) (the outbox/consumer this locks) ·
[Rank & Level System](rank-and-level-system.md) (the other scheduled job this locks)
