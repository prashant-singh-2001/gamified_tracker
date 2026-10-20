package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dao.RankTier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rest of this module's tests run on H2 with the schema generated from the entities and Flyway
 * off, so three kinds of defect cannot show up there: native SQL that ignores Hibernate's
 * default_schema, columns the migrations leave NULL, and JPQL that H2 accepts but Postgres rejects.
 * Each of those once broke the whole XP path in production. This boots a real Postgres 15 (the compose
 * image), runs the real Flyway migrations into the {@code gamification} schema, and lets Hibernate
 * {@code validate} the entities against them, so entity/migration drift fails here too.
 *
 * <p>Needs Docker; skipped (not failed) when it is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=gamification",
        "spring.flyway.default-schema=gamification",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=gamification",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class PostgresNativeQueriesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    // pgjdbc sends the JVM default zone at connect time, and some JDKs report legacy aliases (for example
    // Asia/Calcutta) that the postgres image rejects ("invalid value for parameter TimeZone"). Pin UTC for
    // this class only; the JVM default is put back afterwards.
    private static final TimeZone ORIGINAL_ZONE = TimeZone.getDefault();

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @Autowired
    private LevelTrackerRepository levelTrackerRepository;
    @Autowired
    private UserRankRepository userRankRepository;
    @Autowired
    private UserAchievementRepository userAchievementRepository;
    @Autowired
    private LogXpOutcomeRepository logXpOutcomeRepository;
    @Autowired
    private ManualXpAwardRepository manualXpAwardRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("insertIfAbsent resolves the schema, inserts once, and leaves a row that loads (log_count = 0)")
    void insertIfAbsent() {
        assertEquals(1, levelTrackerRepository.insertIfAbsent(1L, 10L));
        assertEquals(0, levelTrackerRepository.insertIfAbsent(1L, 10L), "second insert is a no-op");

        LevelTracker tracker = levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 10L).orElseThrow();
        assertEquals(0, tracker.getLogCount());
        assertEquals(0.0, tracker.getTotalXp());
    }

    @Test
    @DisplayName("countUsersAhead counts users whose total XP is strictly higher")
    void countUsersAhead() {
        jdbc.update("INSERT INTO gamification.level_tracker (user_id, activity_id, total_xp, current_level_xp, log_count)"
                + " VALUES (1, 1, 100, 0, 1), (1, 2, 50, 0, 1), (2, 1, 120, 0, 1), (3, 1, 300, 0, 1)");

        assertEquals(1, levelTrackerRepository.countUsersAhead(150.0), "only user 3 (300) is above 150");
        assertEquals(2, levelTrackerRepository.countUsersAhead(120.0), "user 1 totals 150 across activities");
        assertEquals(0, levelTrackerRepository.countUsersAhead(300.0));
    }

    @Test
    @DisplayName("findActivityRanking is valid on Postgres and orders by XP descending")
    void activityRanking() {
        jdbc.update("INSERT INTO gamification.level_tracker (user_id, activity_id, total_xp, current_level_xp, log_count)"
                + " VALUES (1, 7, 100, 0, 1), (2, 7, 300, 0, 1), (3, 7, 200, 0, 1), (4, 8, 999, 0, 1)");

        var ranking = levelTrackerRepository.findActivityRanking(7L, PageRequest.of(0, 10));

        assertEquals(List.of(2L, 3L, 1L), ranking.stream().map(r -> r.getUserId()).toList());
        assertEquals(300.0, ranking.get(0).getTotalXp());
    }

    @Test
    @DisplayName("user_rank upsert inserts, then updates the same row, including score, division and the movement baseline")
    void rankUpsert() {
        LocalDateTime baselineAt = LocalDateTime.of(2026, 10, 19, 12, 0);
        userRankRepository.upsert(5L, 100.0, 2, "BASECAMP", 0.5, 3, 6, 80.0, 2, null, null, null, null);
        userRankRepository.upsert(5L, 250.0, 3, "SUMMIT", 0.25, 2, 6, 240.0, 1, "BASECAMP", 2, 3, baselineAt);

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM gamification.user_rank WHERE user_id = 5", Integer.class));
        assertEquals(250.0, jdbc.queryForObject("SELECT total_xp FROM gamification.user_rank WHERE user_id = 5", Double.class));
        assertEquals(2, jdbc.queryForObject("SELECT position FROM gamification.user_rank WHERE user_id = 5", Integer.class));
        assertEquals(240.0, jdbc.queryForObject("SELECT rank_score FROM gamification.user_rank WHERE user_id = 5", Double.class));
        assertEquals(1, jdbc.queryForObject("SELECT division FROM gamification.user_rank WHERE user_id = 5", Integer.class));
        assertEquals("BASECAMP", jdbc.queryForObject("SELECT previous_tier FROM gamification.user_rank WHERE user_id = 5", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT previous_position FROM gamification.user_rank WHERE user_id = 5", Integer.class));
        assertEquals(baselineAt, jdbc.queryForObject("SELECT previous_at FROM gamification.user_rank WHERE user_id = 5", LocalDateTime.class));
    }

    @Test
    @DisplayName("user_rank upsert accepts a missing baseline (all four previous_* columns NULL)")
    void rankUpsertWithoutBaseline() {
        userRankRepository.upsert(6L, 10.0, 1, "ALPINE", 0.3, 4, 10, 10.0, 3, null, null, null, null);

        assertNull(jdbc.queryForObject("SELECT previous_tier FROM gamification.user_rank WHERE user_id = 6", String.class));
        assertNull(jdbc.queryForObject("SELECT previous_at FROM gamification.user_rank WHERE user_id = 6", LocalDateTime.class));
    }

    @Test
    @DisplayName("tier board orders by rank_score, and division floors group by tier and division, on real Postgres")
    void rankScoreQueries() {
        userRankRepository.upsert(11L, 9000.0, 1, "PEAK", 0.1, 3, 20, 40.0, 1, null, null, null, null);
        userRankRepository.upsert(12L, 300.0, 1, "PEAK", 0.1, 3, 20, 280.0, 1, null, null, null, null);
        userRankRepository.upsert(13L, 100.0, 1, "PEAK", 0.14, 4, 20, 90.0, 3, null, null, null, null);

        var board = userRankRepository.findByTierOrderByRankScoreDesc(RankTier.PEAK, PageRequest.of(0, 10));
        assertEquals(List.of(12L, 13L, 11L), board.stream().map(r -> r.getUserId()).toList());

        var floors = userRankRepository.findDivisionFloors();
        assertEquals(2, floors.size());
        assertEquals(40.0, floors.stream().filter(f -> f.getDivision() == 1).findFirst().orElseThrow().getMinScore());
        assertEquals(90.0, floors.stream().filter(f -> f.getDivision() == 3).findFirst().orElseThrow().getMinScore());

        var tierFloors = userRankRepository.findTierFloors();
        assertEquals(1, tierFloors.size());
        assertEquals(40.0, tierFloors.get(0).getMinScore());
    }

    @Test
    @DisplayName("recent awards are read by session time, from both log outcomes and manual awards")
    void awardsSince() {
        jdbc.update("INSERT INTO gamification.log_xp_outcome (log_id, user_id, activity_id, xp_applied, previous_level,"
                + " new_level, leveled_up, applied_at, occurred_at) VALUES"
                + " (1, 1, 1, 50, 1, 1, false, '2026-10-20 10:00:00', '2026-10-19 09:00:00'),"
                + " (2, 1, 1, 70, 1, 1, false, '2026-10-20 10:00:00', '2026-08-01 09:00:00')");
        jdbc.update("INSERT INTO gamification.manual_xp_award (actor_user_id, target_user_id, activity_id, xp, awarded_at)"
                + " VALUES (99, 1, 1, 25, '2026-10-18 08:00:00'), (99, 1, 1, 30, '2026-07-01 08:00:00')");
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 1, 0, 0);

        var logs = logXpOutcomeRepository.findAwardsSince(cutoff);
        var manual = manualXpAwardRepository.findAwardsSince(cutoff);

        assertEquals(1, logs.size(), "the August session is before the cutoff, even though it was applied today");
        assertEquals(50.0, logs.get(0).getXp());
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), logs.get(0).getOccurredAt());
        assertEquals(1, manual.size());
        assertEquals(1L, manual.get(0).getUserId(), "an admin award counts for its target user");
        assertEquals(25.0, manual.get(0).getXp());
    }

    @Test
    @DisplayName("grantIfAbsent grants an achievement once")
    void grantIfAbsent() {
        Long achievementId = jdbc.queryForObject("SELECT id FROM gamification.achievement ORDER BY id LIMIT 1", Long.class);
        assertNotNull(achievementId, "V2 seeds the achievement catalog");

        assertEquals(1, userAchievementRepository.grantIfAbsent(9L, achievementId));
        assertEquals(0, userAchievementRepository.grantIfAbsent(9L, achievementId));
        assertTrue(userAchievementRepository.existsByUserIdAndAchievementId(9L, achievementId));
    }
}
