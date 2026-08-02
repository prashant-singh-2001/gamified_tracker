package com.tracker.activity.repository;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// @Autowired field injection, matching the repo's other @DataJpaTest classes.
@DataJpaTest
public class ActivityLogRepositoryTest {

    @Autowired
    private ActivityLogRepository activityLogRepository;
    @Autowired
    private ActivityRepository activityRepository;

    private static final Set<ReviewStatus> BASELINE_STATUSES = EnumSet.of(ReviewStatus.CLEARED, ReviewStatus.APPROVED);

    private Activity activity(String name, Category category) {
        return activityRepository.save(Activity.builder()
                .name(name).category(category).xpMultiplier(1.0).active(true)
                .createdAt(LocalDateTime.now()).build());
    }

    private ActivityLog log(Long userId, Activity activity, long durationMinutes, ReviewStatus status, LocalDateTime createdAt) {
        return activityLogRepository.save(ActivityLog.builder()
                .userId(userId)
                .activity(activity)
                .startTime(createdAt.minusMinutes(durationMinutes))
                .endTime(createdAt)
                .durationMinutes(durationMinutes)
                .xpEarned(durationMinutes * 1.0)
                .createdAt(createdAt)
                .reviewStatus(status)
                .build());
    }

    @Test
    void findRecentDurationsForUserAndCategory_ordersByCreatedAtDescAndHonoursLimit() {
        Activity study = activity("Study", Category.STUDY);
        LocalDateTime base = LocalDateTime.now().minusDays(10);
        log(1L, study, 10L, ReviewStatus.CLEARED, base.plusDays(1));
        log(1L, study, 20L, ReviewStatus.CLEARED, base.plusDays(2));
        log(1L, study, 30L, ReviewStatus.CLEARED, base.plusDays(3)); // most recent

        List<Long> durations = activityLogRepository.findRecentDurationsForUserAndCategory(
                1L, Category.STUDY, BASELINE_STATUSES, PageRequest.of(0, 2));

        assertEquals(List.of(30L, 20L), durations);
    }

    @Test
    void findRecentDurationsForUserAndCategory_filtersByCategoryViaTheJoinedActivity() {
        Activity study = activity("Study", Category.STUDY);
        Activity gaming = activity("Gaming", Category.GAMING);
        LocalDateTime now = LocalDateTime.now();
        log(1L, study, 60L, ReviewStatus.CLEARED, now);
        log(1L, gaming, 500L, ReviewStatus.CLEARED, now);

        List<Long> durations = activityLogRepository.findRecentDurationsForUserAndCategory(
                1L, Category.STUDY, BASELINE_STATUSES, PageRequest.of(0, 10));

        assertEquals(List.of(60L), durations);
    }

    @Test
    void findRecentDurationsForUserAndCategory_doesNotLeakAnotherUsersRows() {
        Activity study = activity("Study", Category.STUDY);
        LocalDateTime now = LocalDateTime.now();
        log(1L, study, 60L, ReviewStatus.CLEARED, now);
        log(2L, study, 999L, ReviewStatus.CLEARED, now);

        List<Long> durations = activityLogRepository.findRecentDurationsForUserAndCategory(
                1L, Category.STUDY, BASELINE_STATUSES, PageRequest.of(0, 10));

        assertEquals(List.of(60L), durations);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("baseline excludes FLAGGED/REJECTED rows — the poisoning regression test")
    void findRecentDurationsForUserAndCategory_excludesFlaggedAndRejected() {
        Activity study = activity("Study", Category.STUDY);
        LocalDateTime now = LocalDateTime.now();
        log(1L, study, 60L, ReviewStatus.CLEARED, now);
        log(1L, study, 9000L, ReviewStatus.FLAGGED, now.plusMinutes(1));
        log(1L, study, 9000L, ReviewStatus.REJECTED, now.plusMinutes(2));
        log(1L, study, 70L, ReviewStatus.APPROVED, now.plusMinutes(3));

        List<Long> durations = activityLogRepository.findRecentDurationsForUserAndCategory(
                1L, Category.STUDY, BASELINE_STATUSES, PageRequest.of(0, 10));

        assertEquals(2, durations.size());
        assertTrue(durations.containsAll(List.of(60L, 70L)));
    }

    @Test
    void findRecentDurationsForCategory_isTheColdStartFallbackAcrossAllUsers() {
        Activity study = activity("Study", Category.STUDY);
        LocalDateTime now = LocalDateTime.now();
        log(1L, study, 60L, ReviewStatus.CLEARED, now);
        log(2L, study, 70L, ReviewStatus.CLEARED, now.plusMinutes(1));
        log(2L, study, 9000L, ReviewStatus.FLAGGED, now.plusMinutes(2)); // excluded

        List<Long> durations = activityLogRepository.findRecentDurationsForCategory(
                Category.STUDY, BASELINE_STATUSES, PageRequest.of(0, 10));

        assertEquals(2, durations.size());
        assertTrue(durations.containsAll(List.of(60L, 70L)));
    }

    @Test
    void findByReviewStatusOrderByCreatedAtDesc_drivesTheAdminReviewQueue() {
        Activity study = activity("Study", Category.STUDY);
        LocalDateTime now = LocalDateTime.now();
        log(1L, study, 60L, ReviewStatus.CLEARED, now);
        ActivityLog older = log(1L, study, 900L, ReviewStatus.FLAGGED, now.plusMinutes(1));
        ActivityLog newer = log(1L, study, 950L, ReviewStatus.FLAGGED, now.plusMinutes(2));

        List<ActivityLog> queue = activityLogRepository.findByReviewStatusOrderByCreatedAtDesc(
                ReviewStatus.FLAGGED, PageRequest.of(0, 10));

        assertEquals(List.of(newer.getId(), older.getId()),
                queue.stream().map(ActivityLog::getId).toList());
    }
}
