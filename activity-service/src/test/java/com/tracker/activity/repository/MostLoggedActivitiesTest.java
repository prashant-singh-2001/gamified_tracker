package com.tracker.activity.repository;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.dto.RecentActivityProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quick-picks aggregate, run for real: GROUP BY, ordering by aggregates, and the exclusions. */
@DataJpaTest
@DisplayName("Most-logged activities (quick picks)")
class MostLoggedActivitiesTest {

    private static final long USER = 1L;

    @Autowired
    private ActivityLogRepository activityLogRepository;
    @Autowired
    private ActivityRepository activityRepository;

    private Activity activity(String name, Category category, boolean active) {
        return activityRepository.save(Activity.builder().name(name).category(category)
                .xpMultiplier(1.0).active(active).createdAt(LocalDateTime.now()).build());
    }

    private void logs(long userId, Activity activity, int times, LocalDateTime lastStart) {
        for (int i = 0; i < times; i++) {
            LocalDateTime start = lastStart.minusDays(i);
            activityLogRepository.save(ActivityLog.builder().userId(userId).activity(activity)
                    .startTime(start).endTime(start.plusMinutes(30)).durationMinutes(30L).xpEarned(10.0)
                    .createdAt(start.plusMinutes(30)).reviewStatus(ReviewStatus.CLEARED).build());
        }
    }

    private List<RecentActivityProjection> top(long userId, int limit) {
        return activityLogRepository.findMostLoggedActivities(userId, PageRequest.of(0, limit));
    }

    private static List<String> names(List<RecentActivityProjection> picks) {
        return picks.stream().map(RecentActivityProjection::getActivityName).toList();
    }

    @Test
    @DisplayName("most logged first")
    void mostLoggedFirst() {
        Activity few = activity("Few", Category.WORK, true);
        Activity many = activity("Many", Category.STUDY, true);
        Activity middle = activity("Middle", Category.HEALTH, true);
        logs(USER, few, 1, LocalDateTime.of(2026, 10, 10, 9, 0));
        logs(USER, many, 4, LocalDateTime.of(2026, 10, 1, 9, 0));
        logs(USER, middle, 2, LocalDateTime.of(2026, 10, 5, 9, 0));

        assertEquals(List.of("Many", "Middle", "Few"), names(top(USER, 10)));
        assertEquals(4L, top(USER, 10).get(0).getTimesLogged());
    }

    @Test
    @DisplayName("equal counts: the more recently used comes first")
    void tiesBrokenByRecency() {
        Activity older = activity("Older", Category.WORK, true);
        Activity newer = activity("Newer", Category.STUDY, true);
        logs(USER, older, 3, LocalDateTime.of(2026, 10, 1, 9, 0));
        logs(USER, newer, 3, LocalDateTime.of(2026, 10, 9, 9, 0));

        assertEquals(List.of("Newer", "Older"), names(top(USER, 10)));
    }

    @Test
    @DisplayName("equal count AND recency: ordered by id, so the order is total and never reshuffles")
    void fullTiesAreOrderedById() {
        Activity first = activity("First", Category.WORK, true);
        Activity second = activity("Second", Category.STUDY, true);
        LocalDateTime sameMoment = LocalDateTime.of(2026, 10, 9, 9, 0);
        logs(USER, second, 1, sameMoment);
        logs(USER, first, 1, sameMoment);

        assertEquals(List.of("First", "Second"), names(top(USER, 10)));
        assertEquals(names(top(USER, 10)), names(top(USER, 10)), "stable across calls");
    }

    @Test
    @DisplayName("lastUsedAt is the START of the most recent session; category and id are projected")
    void projection() {
        Activity run = activity("Run", Category.HEALTH, true);
        logs(USER, run, 3, LocalDateTime.of(2026, 10, 9, 18, 30));

        RecentActivityProjection pick = top(USER, 5).get(0);

        assertEquals(run.getId(), pick.getActivityId());
        assertEquals("Run", pick.getActivityName());
        assertEquals(Category.HEALTH, pick.getCategory());
        assertEquals(3L, pick.getTimesLogged());
        assertEquals(LocalDateTime.of(2026, 10, 9, 18, 30), pick.getLastUsedAt());
    }

    @Test
    @DisplayName("an INACTIVE activity is never offered, however often it was logged (logging it would be a 409)")
    void inactiveActivitiesAreExcluded() {
        Activity retired = activity("Retired", Category.OTHER, false);
        Activity live = activity("Live", Category.WORK, true);
        logs(USER, retired, 20, LocalDateTime.of(2026, 10, 9, 9, 0));
        logs(USER, live, 1, LocalDateTime.of(2026, 10, 1, 9, 0));

        assertEquals(List.of("Live"), names(top(USER, 10)));
    }

    @Test
    @DisplayName("another user's logs never count towards, or appear in, the caller's picks")
    void otherUsersAreExcluded() {
        Activity shared = activity("Shared", Category.WORK, true);
        Activity theirs = activity("TheirsOnly", Category.STUDY, true);
        logs(USER, shared, 2, LocalDateTime.of(2026, 10, 9, 9, 0));
        logs(2L, shared, 50, LocalDateTime.of(2026, 10, 9, 9, 0));
        logs(2L, theirs, 9, LocalDateTime.of(2026, 10, 9, 9, 0));

        List<RecentActivityProjection> picks = top(USER, 10);

        assertEquals(List.of("Shared"), names(picks));
        assertEquals(2L, picks.get(0).getTimesLogged(), "only the caller's own two logs");
    }

    @Test
    @DisplayName("the limit caps the result")
    void limitCapsTheResult() {
        for (int i = 0; i < 8; i++) {
            logs(USER, activity("A" + i, Category.OTHER, true), i + 1, LocalDateTime.of(2026, 10, 9, 9, 0));
        }

        assertEquals(3, top(USER, 3).size());
        assertEquals(List.of("A7", "A6", "A5"), names(top(USER, 3)));
    }

    @Test
    @DisplayName("every review status counts: a flagged session is still something the user chose to log")
    void allReviewStatusesCount() {
        Activity a = activity("A", Category.WORK, true);
        LocalDateTime start = LocalDateTime.of(2026, 10, 9, 9, 0);
        for (ReviewStatus status : ReviewStatus.values()) {
            activityLogRepository.save(ActivityLog.builder().userId(USER).activity(a).startTime(start)
                    .endTime(start.plusMinutes(30)).durationMinutes(30L).xpEarned(1.0)
                    .createdAt(start).reviewStatus(status).build());
        }

        assertEquals((long) ReviewStatus.values().length, top(USER, 5).get(0).getTimesLogged());
    }

    @Test
    @DisplayName("a user with no logs gets an empty list")
    void emptyForANewUser() {
        assertTrue(top(USER, 5).isEmpty());
    }
}
