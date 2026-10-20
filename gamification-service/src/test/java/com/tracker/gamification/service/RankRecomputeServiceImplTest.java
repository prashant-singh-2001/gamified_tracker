package com.tracker.gamification.service;

import com.tracker.gamification.config.RankingProperties;
import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.dto.TimedXpProjection;
import com.tracker.gamification.dto.UserXpProjection;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.repository.LogXpOutcomeRepository;
import com.tracker.gamification.repository.ManualXpAwardRepository;
import com.tracker.gamification.repository.UserRankRepository;
import com.tracker.gamification.service.impl.RankRecomputeServiceImpl;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rank Recompute Service Tests")
public class RankRecomputeServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Mock
    private LevelTrackerRepository levelTrackerRepository;
    @Mock
    private UserRankRepository userRankRepository;
    @Mock
    private OverallLevelService overallLevelService;
    @Mock
    private LogXpOutcomeRepository logXpOutcomeRepository;
    @Mock
    private ManualXpAwardRepository manualXpAwardRepository;

    private RankRecomputeServiceImpl rankRecomputeService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
        rankRecomputeService = new RankRecomputeServiceImpl(levelTrackerRepository, userRankRepository,
                overallLevelService, logXpOutcomeRepository, manualXpAwardRepository,
                new RankingProperties(14.0, 24), clock);
        lenient().when(overallLevelService.overallLevelFor(anyDouble())).thenReturn(1);
        lenient().when(logXpOutcomeRepository.findAwardsSince(any())).thenReturn(List.of());
        lenient().when(manualXpAwardRepository.findAwardsSince(any())).thenReturn(List.of());
        lenient().when(userRankRepository.findAll()).thenReturn(List.of());
    }

    private record XpRow(Long userId, Double totalXp) implements UserXpProjection {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public Double getTotalXp() {
            return totalXp;
        }
    }

    private record Award(Long userId, Double xp, LocalDateTime occurredAt) implements TimedXpProjection {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public Double getXp() {
            return xp;
        }

        @Override
        public LocalDateTime getOccurredAt() {
            return occurredAt;
        }
    }

    // Argument positions of UserRankRepository.upsert
    private static final int POSITION = 5, SCORE = 7, DIVISION = 8, PREV_TIER = 9, PREV_DIVISION = 10,
            PREV_POSITION = 11, PREV_AT = 12;

    private List<Object[]> upserts() {
        List<Object[]> calls = new ArrayList<>();
        for (var invocation : mockingDetails(userRankRepository).getInvocations()) {
            if (invocation.getMethod().getName().equals("upsert")) {
                calls.add(invocation.getArguments());
            }
        }
        return calls;
    }

    private Object[] upsertFor(long userId) {
        return upserts().stream().filter(a -> a[0].equals(userId)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("recompute returns 0 and upserts nothing when no users are tracked")
    void recompute_doesNothing_whenNoUsersTracked() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of());

        assertEquals(0, rankRecomputeService.recompute());

        assertTrue(upserts().isEmpty());
    }

    @Test
    @DisplayName("a lone user is SUMMIT, division 1, position 1, with no movement baseline (NEW)")
    void recompute_singleUser() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(new XpRow(1L, 500.0)));
        when(overallLevelService.overallLevelFor(500.0)).thenReturn(3);

        assertEquals(1, rankRecomputeService.recompute());

        Object[] call = upsertFor(1L);
        assertEquals("SUMMIT", call[3]);
        assertEquals(3, call[2]);
        assertEquals(1, call[POSITION]);
        assertEquals(1, call[DIVISION]);
        assertNull(call[PREV_TIER]);
        assertNull(call[PREV_POSITION]);
        assertNull(call[PREV_AT]);
    }

    @Test
    @DisplayName("recent form beats lifetime XP: a smaller total earned today outranks a bigger one earned two months ago")
    void recompute_recentFormBeatsLifetime() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(
                new XpRow(1L, 5000.0),  // big lifetime, last earned 56 days ago
                new XpRow(2L, 600.0))); // small lifetime, earned today
        when(logXpOutcomeRepository.findAwardsSince(any())).thenReturn(List.of(
                new Award(1L, 5000.0, NOW.minusDays(56)),
                new Award(2L, 600.0, NOW)));

        rankRecomputeService.recompute();

        assertEquals(1, upsertFor(2L)[POSITION], "the active user leads");
        assertEquals(2, upsertFor(1L)[POSITION]);
        assertEquals(312.5, (double) upsertFor(1L)[SCORE], 1e-6, "5000 aged four half-lives is a sixteenth");
        assertEquals(600.0, (double) upsertFor(2L)[SCORE], 1e-6);
        // lifetime XP is still reported as lifetime XP
        assertEquals(5000.0, (double) upsertFor(1L)[1], 1e-9);
    }

    @Test
    @DisplayName("a user's awards are summed, each aged separately; manual awards count for their target")
    void recompute_sumsAwards_includingManual() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(new XpRow(1L, 300.0)));
        when(logXpOutcomeRepository.findAwardsSince(any())).thenReturn(List.of(
                new Award(1L, 100.0, NOW),                 // full weight
                new Award(1L, 100.0, NOW.minusDays(14)))); // one half-life: half weight
        when(manualXpAwardRepository.findAwardsSince(any())).thenReturn(List.of(
                new Award(1L, 100.0, NOW)));               // an admin award, full weight

        rankRecomputeService.recompute();

        assertEquals(250.0, (double) upsertFor(1L)[SCORE], 1e-6);
    }

    @Test
    @DisplayName("an award dated in the future counts at face value, never more")
    void recompute_futureAwardIsNotAmplified() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(new XpRow(1L, 100.0)));
        when(logXpOutcomeRepository.findAwardsSince(any())).thenReturn(List.of(
                new Award(1L, 100.0, NOW.plusDays(14))));

        rankRecomputeService.recompute();

        assertEquals(100.0, (double) upsertFor(1L)[SCORE], 1e-6);
    }

    @Test
    @DisplayName("lapsed users (score 0) are ordered by lifetime XP instead of all landing in one band")
    void recompute_lifetimeBreaksScoreTies() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(
                new XpRow(1L, 100.0), new XpRow(2L, 900.0), new XpRow(3L, 500.0)));

        rankRecomputeService.recompute();

        assertEquals(1, upsertFor(2L)[POSITION]);
        assertEquals(2, upsertFor(3L)[POSITION]);
        assertEquals(3, upsertFor(1L)[POSITION]);
    }

    @Test
    @DisplayName("only users equal on score AND lifetime XP share a position (dense ranking)")
    void recompute_tiedUsersShareMinimumPosition() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(
                new XpRow(1L, 500.0), new XpRow(2L, 400.0), new XpRow(3L, 400.0), new XpRow(4L, 100.0)));

        rankRecomputeService.recompute();

        assertEquals(1, upsertFor(1L)[POSITION]);
        assertEquals(2, upsertFor(2L)[POSITION]);
        assertEquals(2, upsertFor(3L)[POSITION], "tied users share the tie group's minimum position");
        assertEquals(4, upsertFor(4L)[POSITION], "the next user continues from the raw count");
        assertEquals(0.75, (double) upsertFor(4L)[4], 1e-9, "percentile is the top fraction");
    }

    @Test
    @DisplayName("20 users split into the exact tier bracket sizes the percentile formula implies")
    void recompute_twentyUsers_splitIntoExpectedTierCounts() {
        List<UserXpProjection> rows = new ArrayList<>();
        List<TimedXpProjection> awards = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(new XpRow((long) (i + 1), 2000.0 - i * 10));
            awards.add(new Award((long) (i + 1), 2000.0 - i * 10, NOW)); // strictly descending scores
        }
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(rows);
        when(logXpOutcomeRepository.findAwardsSince(any())).thenReturn(awards);

        rankRecomputeService.recompute();

        long[] expected = {1, 2, 2, 5, 2, 3, 2, 2, 1};
        RankTier[] tiers = RankTier.values();
        for (int t = 0; t < tiers.length; t++) {
            final String name = tiers[t].name();
            assertEquals(expected[t], upserts().stream().filter(a -> a[3].equals(name)).count(), name);
        }
    }

    @Test
    @DisplayName("each tier is cut into thirds: divisions run 1 (best) to 3 inside a tier")
    void recompute_assignsDivisions() {
        List<UserXpProjection> rows = new ArrayList<>();
        List<TimedXpProjection> awards = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(new XpRow((long) (i + 1), 2000.0 - i * 10));
            awards.add(new Award((long) (i + 1), 2000.0 - i * 10, NOW));
        }
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(rows);
        when(logXpOutcomeRepository.findAwardsSince(any())).thenReturn(awards);

        rankRecomputeService.recompute();

        // ALPINE is [0.25, 0.50): positions 6..10 of 20, thirds of width 1/12
        assertEquals("ALPINE", upsertFor(6L)[3]);
        assertEquals(1, upsertFor(6L)[DIVISION]);  // f = 0.25
        assertEquals(1, upsertFor(7L)[DIVISION]);  // f = 0.30
        assertEquals(2, upsertFor(8L)[DIVISION]);  // f = 0.35
        assertEquals(2, upsertFor(9L)[DIVISION]);  // f = 0.40
        assertEquals(3, upsertFor(10L)[DIVISION]); // f = 0.45
    }

    private UserRank existingRank(long userId, RankTier tier, int division, int position) {
        UserRank rank = new UserRank(100.0, 1, tier, 0.5, position, 4, NOW.minusMinutes(1), userId);
        rank.setDivision(division);
        return rank;
    }

    @Test
    @DisplayName("a user already ranked with no baseline gets one: their pre-update state, stamped now")
    void recompute_startsABaselineForAnExistingUser() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(new XpRow(1L, 500.0)));
        when(userRankRepository.findAll()).thenReturn(List.of(existingRank(1L, RankTier.ALPINE, 2, 3)));

        rankRecomputeService.recompute();

        Object[] call = upsertFor(1L);
        assertEquals("ALPINE", call[PREV_TIER]);
        assertEquals(2, call[PREV_DIVISION]);
        assertEquals(3, call[PREV_POSITION]);
        assertEquals(NOW, call[PREV_AT]);
    }

    @Test
    @DisplayName("a fresh baseline is carried forward unchanged, so movement accumulates over the window")
    void recompute_keepsAFreshBaseline() {
        UserRank before = existingRank(1L, RankTier.RIDGE, 1, 2);
        before.setPreviousTier(RankTier.ALPINE);
        before.setPreviousDivision(3);
        before.setPreviousPosition(9);
        before.setPreviousAt(NOW.minusHours(5));
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(new XpRow(1L, 500.0)));
        when(userRankRepository.findAll()).thenReturn(List.of(before));

        rankRecomputeService.recompute();

        Object[] call = upsertFor(1L);
        assertEquals("ALPINE", call[PREV_TIER]);
        assertEquals(3, call[PREV_DIVISION]);
        assertEquals(9, call[PREV_POSITION]);
        assertEquals(NOW.minusHours(5), call[PREV_AT]);
    }

    @Test
    @DisplayName("a baseline older than the movement window rolls forward to the pre-update state")
    void recompute_rollsAnExpiredBaseline() {
        UserRank before = existingRank(1L, RankTier.RIDGE, 1, 2);
        before.setPreviousTier(RankTier.ALPINE);
        before.setPreviousDivision(3);
        before.setPreviousPosition(9);
        before.setPreviousAt(NOW.minusHours(30)); // older than 24h
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(new XpRow(1L, 500.0)));
        when(userRankRepository.findAll()).thenReturn(List.of(before));

        rankRecomputeService.recompute();

        Object[] call = upsertFor(1L);
        assertEquals("RIDGE", call[PREV_TIER]);
        assertEquals(1, call[PREV_DIVISION]);
        assertEquals(2, call[PREV_POSITION]);
        assertEquals(NOW, call[PREV_AT]);
    }

    @Test
    @DisplayName("issue #82: recompute carries @SchedulerLock with a non-empty name, so a future "
            + "refactor can't silently drop cross-instance locking")
    void recompute_isGuardedBySchedulerLock() throws NoSuchMethodException {
        Method method = RankRecomputeServiceImpl.class.getDeclaredMethod("recompute");

        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

        assertNotNull(lock, "recompute must stay annotated with @SchedulerLock");
        assertFalse(lock.name().isBlank(), "SchedulerLock name must be set");
        assertFalse(lock.lockAtMostFor().isBlank(), "lockAtMostFor must be set, and stay below the recompute interval");
    }
}
