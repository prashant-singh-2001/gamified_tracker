# TODO — Overall Level, Percentile Ranks & Within-Rank Leaderboards (issue #13, part 2)

> Step-by-step implementation checklist for a per-user **overall level**, a **percentile-based rank**,
> and **within-rank leaderboards** (Clash-of-Clans style) in `gamification-service`.
> This is a **planning document only** — no code has been written yet. Each `- [ ]` is one step, with a
> **Why** (the reason it exists) and an **Accept** (how you know it's done).
>
> Extends [ACHIEVEMENTS_AND_LEADERBOARDS_TODO.md](ACHIEVEMENTS_AND_LEADERBOARDS_TODO.md) — this rank
> system **replaces that doc's simpler "global leaderboard" step**; its per-activity leaderboard still stands.

## The three things being built (and why they're distinct)

| Concept | Type | Derived from | Why separate |
|---|---|---|---|
| **Overall level** | Absolute, stable | Your own total XP vs a fixed curve | Personal progression — never goes down, gives steady dopamine as XP climbs |
| **Rank** (Summit tier) | Relative, dynamic | Your percentile among **all** users | Competitive standing — shifts as others earn XP, so you can be **promoted or demoted** (like CoC leagues) |
| **Within-rank leaderboard** | Relative, per-tier | Your XP vs others **in your tier** | Gives every tier its own race, so mid-table users still compete instead of staring at an unreachable #1 |

> **Key consequence to accept up front:** because ranks are **percentile-based**, they are *relative*. A
> user who stops logging can drop from Summit to Peak purely because others overtook them. This is
> intentional and is exactly what makes the within-rank boards meaningful.

## Locked decisions

| Decision | Choice | Why |
|---|---|---|
| Rank names | **Summit / Ascent** ladder | Mountaineering theme fits the "long cumulative climb" of total XP; higher tier = higher altitude |
| Rank compute | **Scheduled snapshot** → `user_rank` table | Ranking needs *all* users sorted; doing that per-request is O(N) per viewer. A periodic batch makes reads trivial and consistent |
| Overall level curve | **Seeded threshold table** | Reuses the existing per-activity level pattern; lets you retune the curve by editing seed data, no redeploy |

## Rank ladder & percentile mapping (the single source of truth for the math)

Sort all users by `totalXp` **descending**; `position` is 1-based (`1` = highest XP).
`topFraction = (position - 1) / totalUsers` → the #1 user is `0.0`; a lone user is `0.0` (Summit).
Bands are half-open `[lo, hi)` (last inclusive).

| User's bracket | topFraction | **Summit tier** | (your military name) |
|---|---|---|---|
| top 5% | `[0.00, 0.05)` | **SUMMIT** | General |
| 5–15% | `[0.05, 0.15)` | **PEAK** | Brigadier |
| 15–25% | `[0.15, 0.25)` | **RIDGE** | Colonel |
| 25–50% | `[0.25, 0.50)` | **ALPINE** | Major |
| 50–60% | `[0.50, 0.60)` | **ASCENT** | Captain |
| 60–75% | `[0.60, 0.75)` | **HIGHLAND** | Subedar |
| 75–85% | `[0.75, 0.85)` | **FOOTHILL** | Havaldar |
| 85–95% | `[0.85, 0.95)` | **TRAILHEAD** | Naik |
| 95–100% | `[0.95, 1.00]` | **BASECAMP** | Sepoy |

- **Ties:** users with equal XP share the tie group's **minimum** position, so equal XP always lands in
  the same tier (no arbitrary boundary splits).
- **Small N:** the formula still gives everyone a tier (N=1 → Summit). Middle tiers are naturally sparse
  with few users — see step D.2 for the optional `min-users-for-ranks` gate.

## Repo conventions to honor
- **Comment out old code, don't delete it** when replacing a line.
- **Do not commit** — the maintainer commits.
- User-scoped endpoints read the trusted **`@RequestHeader("userId")`**, never the body (IDOR fix).
- Schema is **Hibernate-managed** (`ddl-auto`); `data.sql` seeds rows.

## Building blocks to reuse (verified against current source)
- **Total-XP source of truth** — [LevelTrackerRepository.getTotalXpByUserId](gamification-service/src/main/java/com/tracker/gamification/repository/LevelTrackerRepository.java#L30).
- **Level-curve pattern** — [ActivityLevelThresholdRepository.findReachedLevels](gamification-service/src/main/java/com/tracker/gamification/repository/ActivityLevelThresholdRepository.java).
- **`@Scheduled` precedent** — [activity-service OutboxRelay](activity-service/src/main/java/com/tracker/activity/outbox/OutboxRelay.java); note `@EnableScheduling` currently sits on `ActivityServiceApplication` **only**.
- **Idempotent upsert** — `LevelTracker` unique constraint + `insertIfAbsent`.
- **Header-driven, ownership-scoped API** — [NotificationController](gamification-service/src/main/java/com/tracker/gamification/controller/NotificationController.java).
- **Gateway route** — [RouteConfiguration.gamificationRoute](api-gateway/src/main/java/com/tracker/gateway/config/RouteConfiguration.java#L37).

---

## Section A — Overall level (absolute, from total XP)

### A.1 `OverallLevelThreshold` entity
- [ ] Add `OverallLevelThreshold` (`@Id Integer level`, `double xpRequired`) in `dao`.
  - **Why:** a global level curve needs a lookup of "what XP does level N require?" A single-column PK
    (unlike the per-activity `@EmbeddedId`) is enough because this curve isn't activity-scoped.
  - **Accept:** table `overall_level_threshold` is created by `ddl-auto` on boot.

### A.2 Seed the curve
- [ ] Add rows to `gamification-service/src/main/resources/data.sql` for a **non-linear** curve
  (e.g. L1=0, L2=100, L3=250, L4=500, L5=1000, … each step larger), using idempotent inserts
  (`INSERT … ON CONFLICT (level) DO NOTHING`).
  - **Why:** a widening curve keeps early levels quick (retention) and later levels meaningful. Seeding
    (not hardcoding) lets you retune balance by editing SQL — no redeploy.
  - **Accept:** rows exist after boot; a second boot doesn't duplicate them. If the seed races schema
    creation, set `spring.jpa.defer-datasource-initialization: true`.

### A.3 Global `findReachedLevels` query
- [ ] Add `OverallLevelThresholdRepository.findReachedLevels(double xp, Pageable)` = "levels whose
  `xpRequired <= :xp`, ordered by level **DESC**" (clone of the activity query, minus `activityId`).
  - **Why:** the highest row you qualify for **is** your level. Ordering desc + `PageRequest.of(0,1)`
    returns exactly that in one query — the same trick already used for per-activity levels.
  - **Accept:** for XP just below a threshold you get the lower level; at/above it, the higher one.

### A.4 Level helper
- [ ] Add `overallLevelFor(double totalXp)` (in a `LevelService` or the recompute service) returning the
  highest reached level, defaulting to level 1 when the result is empty.
  - **Why:** centralizes the "XP → level" rule so both the batch job and any live update use identical
    logic (no drift).
  - **Accept:** unit test maps sample XP values to expected levels, including the empty/below-L1 case.

---

## Section B — Rank snapshot + scheduled recompute

### B.1 `RankTier` enum (the band math)
- [ ] Add enum `RankTier` with the 9 Summit tiers, each carrying its `[lo, hi)` bounds, plus a static
  `fromTopFraction(double topFraction)` returning the matching tier.
  - **Why:** one authoritative place for the percentile→tier mapping. Encoding bounds *in* the enum means
    the controller, batch job, and tests can't disagree about where boundaries fall.
  - **Accept:** `fromTopFraction(0.0)`→SUMMIT, `0.05`→PEAK, `0.94`→TRAILHEAD, `0.95`→BASECAMP, etc.

### B.2 `UserRank` snapshot entity
- [ ] Add `UserRank`: `userId` (**unique**), `totalXp`, `overallLevel`, `rankName` (`@Enumerated STRING`),
  `percentile` (double), `position` (int), `totalUsers` (int), `updatedAt`.
  - **Why:** this is the **materialized view** the batch writes. Storing the snapshot (not recomputing on
    every read) is what makes rank/leaderboard reads O(1)/O(page). `totalUsers`/`updatedAt` let the UI
    show "#42 of 380, updated 3 min ago".
  - **Accept:** unique constraint on `user_id`; one row per user.

### B.3 `UserRankRepository`
- [ ] Add `findByUserId`, `findByRankNameOrderByTotalXpDesc(RankTier, Pageable)`, `countByRankName`, and a
  native idempotent upsert (`INSERT … ON CONFLICT (user_id) DO UPDATE SET …`).
  - **Why:** `findByRankName…` *is* the within-rank leaderboard query; `countByRankName` powers the tier
    distribution; the upsert lets the batch rewrite a user's snapshot without a delete+insert race.
  - **Accept:** upsert inserts first time, updates thereafter; ordering query returns tier members by XP desc.

### B.4 `findAllUserTotals` ranking query
- [ ] Add `LevelTrackerRepository.findAllUserTotals()` = `SELECT l.userId AS userId, SUM(l.totalXp) AS
  totalXp FROM LevelTracker l GROUP BY l.userId ORDER BY SUM(l.totalXp) DESC` (projection interface).
  - **Why:** the batch needs every user's cross-activity total in ranked order in one pass. This
    generalizes the existing single-user `getTotalXpByUserId`.
  - **Accept:** `@DataJpaTest` confirms grouping + descending order across multiple users/activities.

### B.5 `RankRecomputeService.recompute()`
- [ ] Implement `@Transactional recompute()`: load `findAllUserTotals()`, set `N = size`; iterate with a
  1-based `position`, compute `topFraction = (position-1)/N`, resolve `RankTier.fromTopFraction`, compute
  `overallLevelFor(totalXp)`, and **upsert** each `UserRank` (rankName, percentile=topFraction, position,
  totalUsers=N). Handle ties by reusing the tie group's min position.
  - **Why:** this is the heart of the feature — it turns a raw XP ranking into tiers + levels in a single
    ordered sweep. `@Transactional` means readers never see a half-rebuilt snapshot.
  - **Accept:** for a known 20-user distribution the per-tier counts match the bracket widths
    (≈ 1/2/1/5/1/3/1/1/1) and levels match the seeded curve.

### B.6 Enable scheduling + wire the trigger
- [ ] Annotate `recompute()` with `@Scheduled(fixedDelayString =
  "${ranking.recompute-interval-ms:300000}")` **and add `@EnableScheduling` to
  `GamificationServiceApplication`**.
  - **Why:** `@Scheduled` is inert without `@EnableScheduling`, which today lives only on the activity
    app — forgetting this is the most likely "it silently never runs" bug. `fixedDelay` (not `fixedRate`)
    avoids overlapping runs if a recompute is slow.
  - **Accept:** logs show the job firing on the configured interval; snapshot `updatedAt` advances.
- [ ] Add an admin `POST /ranks/recompute` endpoint that calls `recompute()`.
  - **Why:** waiting 5 minutes to test is painful; an on-demand trigger makes local/QA verification and
    demos instant.
  - **Accept:** hitting the endpoint refreshes `user_rank` immediately.

### B.7 (Optional) live overall-level feedback
- [ ] In [LevelTrackerServiceImpl.save](gamification-service/src/main/java/com/tracker/gamification/service/impl/LevelTrackerServiceImpl.java#L88),
  after XP is applied, update the user's `UserRank.totalXp` + `overallLevel` immediately (leave
  rank/percentile to the next batch). Additive; comment-out-don't-delete if a line must change.
  - **Why:** absolute level *can* be known instantly, so showing it rise the moment XP is earned feels
    responsive — while rank (which needs everyone else) refreshes each cycle, mirroring CoC's "trophies
    update live, league refreshes each season."
  - **Accept:** logging activity bumps overall level in the same request; rank changes only after a recompute.

---

## Section C — Within-rank leaderboards + rank API (CoC-style)

### C.1 DTOs
- [ ] Add `RankCardDto` (rankName, overallLevel, totalXp, percentile, position, totalUsers, updatedAt),
  `LeaderboardEntryDto` (withinRankPosition, userId, totalXp, overallLevel, rankName),
  `RankDistributionDto` (rankName, userCount).
  - **Why:** DTOs keep entities out of the API and let each endpoint return exactly what its screen needs
    (a profile card vs a leaderboard row vs a distribution bar).
  - **Accept:** clean JSON; `percentile`/positions are 1-based and human-readable.

### C.2 `RankController` (`@RequestMapping("/ranks")`)
- [ ] `GET /me` (`@RequestHeader("userId") Long userId`) → the caller's `RankCardDto`.
  - **Why:** the "my standing" screen; header-scoped so a user can't read someone else's card (IDOR-safe).
  - **Accept:** returns the caller's tier/level/percentile; 404/empty handled if not yet ranked.
- [ ] `GET /{rank}/leaderboard` (paged) → members of that tier by XP desc.
  - **Why:** the CoC-style within-tier race; paging keeps large tiers cheap.
  - **Accept:** only that tier's users, ordered by XP; `withinRankPosition` is contiguous per page.
- [ ] `GET /me/leaderboard` → the caller's own tier board.
  - **Why:** convenience — the client shouldn't need to know its tier name first.
  - **Accept:** equivalent to `/{myRank}/leaderboard`.
- [ ] `GET /` → tier distribution (`countByRankName`).
  - **Why:** powers a "how many at each altitude" dashboard chart and is a quick health check on the batch.
  - **Accept:** counts sum to `totalUsers`.

### C.3 Gateway route
- [ ] Add `/api/ranks/**` to the [gamificationRoute](api-gateway/src/main/java/com/tracker/gateway/config/RouteConfiguration.java#L37) predicate.
  - **Why:** without it the new controller isn't reachable through the gateway (no JWT, no `userId`
    injection). `stripPrefix(1)` already maps `/api/ranks/**` → `/ranks/**` on the service.
  - **Accept:** `GET http://localhost:8080/api/ranks/me` reaches the controller with the header injected.

---

## Section D — Config, seed & robustness

### D.1 Scheduling config
- [ ] Add `ranking.recompute-interval-ms` (default `300000`) to
  [application.yaml](gamification-service/src/main/resources/application.yaml).
  - **Why:** externalizing the cadence lets you tune freshness-vs-cost per environment without a rebuild.
  - **Accept:** overriding it changes the observed interval.

### D.2 (Optional) small-user-base gate
- [ ] Add `ranking.min-users-for-ranks` (e.g. 20); below it, mark everyone unranked/BASECAMP.
  - **Why:** percentile tiers are meaningless with 3 users (someone is instantly "top 5%"). Gating avoids
    a hollow "General" badge until the population justifies it.
  - **Accept:** with N below the gate, `GET /ranks/me` reports unranked; above it, real tiers appear.

### D.3 Docs & Postman (follow-up)
- [ ] Add a `docs/features/rank-and-level-system.md` showcase doc + index row; add a Postman folder for
  the `/ranks/**` endpoints (JWT + `userId` via the gateway).
  - **Why:** keeps this on par with the other 11 feature docs and makes the endpoints easy to demo.
  - **Accept:** doc renders; Postman requests succeed end-to-end.

---

## Section E — Tests (why each layer)

- [ ] **Unit — `RankTier.fromTopFraction` boundaries:** exact `0.05/0.15/0.25/0.50/0.60/0.75/0.85/0.95`,
  `N=1`, and tie cases.
  - **Why:** boundary rounding is the single most error-prone part; lock it with a table test.
- [ ] **Unit — `recompute()` distribution:** feed a known 20-user set, assert per-tier counts + levels.
  - **Why:** proves the ordered-sweep + mapping produce the intended tier shape end-to-end in memory.
- [ ] **`@DataJpaTest`:** `findAllUserTotals` grouping/order; global `findReachedLevels`;
  `UserRankRepository.findByRankNameOrderByTotalXpDesc`; `user_rank` unique/upsert.
  - **Why:** these are native/derived queries + a DB constraint — only a real (H2) DB validates them.
- [ ] **`@WebMvcTest(RankController)`:** routes, `userId` header usage, paging.
  - **Why:** verifies the web layer/IDOR-safety without a full context.
- [ ] **Integration:** seed LevelTracker rows → `recompute()` → assert tiers + within-rank ordering; add
  XP + recompute → observe **promotion and demotion**.
  - **Why:** the dynamic/relative behavior (the whole point) only shows up when the population changes.

---

## Verification (end-to-end)
- [ ] `mvn -q verify` in `gamification-service` — tests green; JaCoCo report at
  `gamification-service/target/site/jacoco/index.html`.
- [ ] `docker compose up`; create several users, log activities across them.
- [ ] `POST /api/ranks/recompute` (or wait for the schedule) → `GET /api/ranks/me` shows tier + overall
  level + percentile; `GET /api/ranks/{rank}/leaderboard` lists that tier by XP; `GET /api/ranks` shows
  the distribution.
- [ ] Push one user's XP up, recompute → they climb tiers and someone else can drop (confirms the
  percentile system is live and relative).
- [ ] Confirm `/api/ranks/**` resolves through `lb://gamification-service` with JWT enforced.

---

_Grounded against branch `13-add-achievementsbadges-and-leaderboards` on 2026-07-19. Class/method
references were verified against source; confirm against the tree before relying on line-level detail._
