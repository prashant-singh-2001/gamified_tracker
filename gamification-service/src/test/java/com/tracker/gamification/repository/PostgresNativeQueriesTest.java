package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LevelTracker;
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
    @DisplayName("user_rank upsert inserts, then updates the same row")
    void rankUpsert() {
        userRankRepository.upsert(5L, 100.0, 2, "BASECAMP", 0.5, 3, 6);
        userRankRepository.upsert(5L, 250.0, 3, "SUMMIT", 0.25, 2, 6);

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM gamification.user_rank WHERE user_id = 5", Integer.class));
        assertEquals(250.0, jdbc.queryForObject("SELECT total_xp FROM gamification.user_rank WHERE user_id = 5", Double.class));
        assertEquals(2, jdbc.queryForObject("SELECT position FROM gamification.user_rank WHERE user_id = 5", Integer.class));
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
