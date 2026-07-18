# Testing Strategy — Sliced, Fast, Hermetic

**Services:** all four · **27 test classes** · **Key techniques:** test slices (`@WebMvcTest`,
`@DataJpaTest`), Mockito unit tests, `InOrder`/`ArgumentCaptor`, H2 for the persistence layer,
`@MockBean` to keep infra-dependent context tests hermetic

## What it is / why it's notable

The suite is built the way a Spring test suite *should* be — as a pyramid of fast, focused slices
rather than a pile of slow full-context integration tests. 27 test classes across the four modules,
each using the narrowest Spring test slice that still exercises the thing under test (or no Spring
context at all, where a plain constructor call does the job). The interesting parts aren't the counts
— they're the specific choices that keep the suite fast and deterministic: verifying **ordering** of
side effects (did the log save *before* the outbox write?), capturing and inspecting emitted
messages, and neutralizing external infra (Redis, RabbitMQ) so even the full-context tests boot
without Docker.

## The slice pyramid

| Slice | Count | What it tests | Example |
|---|---|---|---|
| Plain unit (`@ExtendWith(MockitoExtension.class)` or none) | 8 | Service/domain logic, mocks at the boundary | `LevelTrackerServiceImplTest`, `ActivityTest` |
| `@DataJpaTest` (H2) | 6 | Repository queries against a real (in-memory) DB | `LevelTrackerRepositoryTest`, `OutboxEventRepositoryTest` |
| `@WebMvcTest` (MockMvc) | 6 | Controller HTTP contract, JSON, status codes | `NotificationControllerTest`, `ActivityControllerTest` |
| `@SpringBootTest` | 4 | One context-load smoke test per service | `ApiGatewayApplicationTests` |

Each slice loads only the beans it needs — a `@WebMvcTest` doesn't spin up JPA or Redis, a
`@DataJpaTest` doesn't start the web layer — so the vast majority of tests run in milliseconds.

## Techniques worth calling out

### 1. Testable-by-design services → plain unit tests

`ActivityLogServiceImpl`, `LevelTrackerServiceImpl`, `OutboxRelay`, and `ActivityLoggedListener` all
use constructor injection, so their tests need no Spring context at all — just `new` the class with
mocks. `Activity.effectiveXpMultiplier()` is pure logic, tested with a plain builder and zero
framework (`ActivityTest`). Fast tests are a *consequence* of the production design, not a separate
effort.

### 2. Verifying the *order* of side effects — `InOrder`

The whole correctness of the [event-driven outbox](event-driven-decoupling.md) hinges on the log
being saved *before* the outbox row, and the dedup guard being written *before* XP is applied. Tests
assert exactly that, not just "both happened":
```java
// ActivityLogServiceImplTest — the #4 save-order fix
InOrder inOrder = inOrder(activityLogRepository, outboxEventRepository);
inOrder.verify(activityLogRepository).save(any(ActivityLog.class));
inOrder.verify(outboxEventRepository).save(any(OutboxEvent.class));
```
```java
// ActivityLoggedListenerTest — dedup guard written before XP applied
InOrder inOrder = inOrder(processedEventRepository, levelTrackerService);
inOrder.verify(processedEventRepository).save(...);
inOrder.verify(levelTrackerService).save(...);
```

### 3. Inspecting emitted messages — `ArgumentCaptor` + real serialization

`OutboxRelayTest` doesn't just verify a message was sent — it captures it and asserts on its content,
using a real `ObjectMapper` so the payload round-trip is genuinely exercised:
```java
ArgumentCaptor<ActivityLoggedEvent> captor = ArgumentCaptor.forClass(ActivityLoggedEvent.class);
verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), captor.capture());
assertEquals(1L, captor.getValue().logId());
assertNotNull(row.getPublishedAt(), "publishedAt is stamped only after a successful send");
```
And it tests the failure path — a throwing broker must **not** crash the scheduled poll and must
leave `publishedAt` null for retry:
```java
doThrow(new RuntimeException("broker unreachable"))
        .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ActivityLoggedEvent.class));
assertDoesNotThrow(() -> relay.publishPending());
assertNull(row.getPublishedAt(), "left unpublished so it is retried on the next tick");
```

### 4. Deterministic assertions against randomness

XP tests can't hardcode `xpEarned`, because a ~20% random bonus roll is baked into it. Instead they
reconstruct the expected value from the bonus the response *reports*, so the assertion is exact
regardless of the roll:
```java
// ActivityLogServiceImplTest — issue #10 category-default XP
assertEquals(body.durationMinutes() * 1.5 * body.bonusMultiplier(), body.xpEarned(), 1e-9);
```

### 5. Hermetic full-context tests — mocking away Redis/RabbitMQ

The gateway's `RateLimitConfig` eagerly opens a Redis connection at startup, which would make a naive
`@SpringBootTest` fail outside `docker-compose`. Rather than require a real or embedded Redis, the
context-load test `@MockBean`s the offending beans so the context still wires end-to-end, hermetically:
```java
@SpringBootTest
class ApiGatewayApplicationTests {
    @MockBean private RedisClient redisClient;
    @MockBean private StatefulRedisConnection<String, byte[]> redisConnection;
    @MockBean private AsyncProxyManager<String> asyncProxyManager;
    @MockBean private ProxyManager<String> proxyManager;

    @Test
    void contextLoads() {}
}
```

### 6. In-memory persistence — H2 per module

Each service's `src/test/resources/application.properties` swaps Postgres for H2 so `@DataJpaTest`
slices run with no external database:
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.sql.init.mode=never
```
Note this means Postgres-specific SQL (the `ON CONFLICT` upsert in
[concurrency-safe XP](concurrency-safe-xp.md)) isn't exercised by the H2 slices — its behavior is
verified at the service layer with mocks and, historically, a live concurrent-burst test.

## What's covered

The critical paths all have tests: the IDOR header wiring, the outbox producer + relay + idempotent
consumer, concurrency-safe `save()` + archiving, the `read`/`is_read` derived-query regression guard,
notification ownership scoping, and the XP-multiplier resolution.

## Known gap (honest inventory)

There's no Testcontainers / end-to-end suite spanning gateway → activity-service → RabbitMQ →
gamification-service against real infra — every test is per-service with mocks or H2 at the boundary
(a known backlog item).

## Try it

```bash
mvn -f activity-service/pom.xml test
mvn -f gamification-service/pom.xml test
mvn -f api-gateway/pom.xml test
```

## Related
[Event-Driven Decoupling](event-driven-decoupling.md) (what the `InOrder`/`ArgumentCaptor` tests
guard) · [Concurrency-Safe XP Accumulation](concurrency-safe-xp.md) · [Rate Limiting](rate-limiting.md)
(the beans mocked in the context test)
