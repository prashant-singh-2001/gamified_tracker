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
    List<ActivityLog> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<ActivityLog> findByUserIdAndStartTimeAfter(Long userId, LocalDateTime after);
}
