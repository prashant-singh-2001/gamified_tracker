package com.tracker.activity.repository;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.ReviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByUserId(Long userId);

    // Session integrity (#67): per-user duration baseline for the outlier detector.
    // SECURITY: baselineStatuses must only ever be {CLEARED, APPROVED}. Including FLAGGED or
    // REJECTED rows would let a farmer poison their own baseline — submit a run of huge
    // sessions, and the median they shift makes every subsequent huge session look normal.
    // Category lives on the joined Activity, not on activity_log.
    @Query("""
            SELECT l.durationMinutes FROM ActivityLog l
            JOIN l.activity a
            WHERE l.userId = :userId
              AND a.category = :category
              AND l.durationMinutes IS NOT NULL
              AND l.reviewStatus IN :baselineStatuses
            ORDER BY l.createdAt DESC
            """)
    List<Long> findRecentDurationsForUserAndCategory(@Param("userId") Long userId,
                                                       @Param("category") Category category,
                                                       @Param("baselineStatuses") Collection<ReviewStatus> baselineStatuses,
                                                       Pageable limit);

    // Cold-start fallback when a user has fewer than min-samples logs of their own: the same
    // baseline, across all users, for the category. Same poisoning caveat as above; note this
    // fallback is itself poisonable in aggregate if many users farm the same category — accepted
    // at this scale, follow-up is a trimmed mean or admin-curated baseline.
    @Query("""
            SELECT l.durationMinutes FROM ActivityLog l
            JOIN l.activity a
            WHERE a.category = :category
              AND l.durationMinutes IS NOT NULL
              AND l.reviewStatus IN :baselineStatuses
            ORDER BY l.createdAt DESC
            """)
    List<Long> findRecentDurationsForCategory(@Param("category") Category category,
                                               @Param("baselineStatuses") Collection<ReviewStatus> baselineStatuses,
                                               Pageable limit);

    // Drives the admin review queue.
    List<ActivityLog> findByReviewStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable page);

    // Session integrity (#67): per-user-per-day aggregate cap. A withheld (FLAGGED) or denied
    // (REJECTED) log must not consume the user's legitimate daily budget, so countedStatuses
    // mirrors the baseline-exclusion rationale above: {CLEARED, APPROVED} only.
    // Range predicate on startTime rather than a date-truncation function: date_trunc is
    // Postgres-only and would die on the H2 test slice; a plain >= / <= comparison is
    // dialect-neutral. Boxed Long return: SUM(...) is SQL NULL for an empty group, and a
    // primitive getter would throw on unboxing (same convention as UserXpProjection).
    @Query("""
            SELECT SUM(l.durationMinutes) FROM ActivityLog l
            WHERE l.userId = :userId
              AND l.startTime >= :dayStart AND l.startTime <= :dayEnd
              AND l.reviewStatus IN :countedStatuses
            """)
    Long sumDurationForUserOnDay(@Param("userId") Long userId,
                                  @Param("dayStart") LocalDateTime dayStart,
                                  @Param("dayEnd") LocalDateTime dayEnd,
                                  @Param("countedStatuses") Collection<ReviewStatus> countedStatuses);
}
