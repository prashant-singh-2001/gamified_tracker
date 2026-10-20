package com.tracker.activity.repository;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.dto.RecentActivityProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {
    List<ActivityLog> findByUserId(Long userId);

    // DELETE /activity/{id}: is this activity part of anyone's history? Decides soft vs hard delete.
    boolean existsByActivityId(Long activityId);

    // #78/#88: ownership-scoped lookup for GET /activitylog/{id} -- a miss here (wrong owner or
    // no such row) is intentionally indistinguishable from a genuinely missing id, so the handler
    // renders the same 404 either way instead of leaking existence via a 403.
    Optional<ActivityLog> findByIdAndUserId(Long id, Long userId);

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
    List<ActivityLog> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<ActivityLog> findByUserIdAndStartTimeAfter(Long userId, LocalDateTime after);

    // Quick picks (GET /activitylog/user/{id}/recent): the caller's most-logged ACTIVE activities.
    // Inactive ones are left out because logging against them is a 409 -- offering one as a shortcut
    // would just hand the user an error. Most logged first, then most recently used, then id so the
    // order is total (a tie must not reshuffle between calls). Pageable is only the result limiter,
    // as everywhere else in this repo. All review statuses count: a flagged session is still
    // something the user chose to log.
    @Query("""
            SELECT l.activity.id AS activityId, l.activity.name AS activityName,
                   l.activity.category AS category, COUNT(l) AS timesLogged,
                   MAX(l.startTime) AS lastUsedAt
            FROM ActivityLog l
            WHERE l.userId = :userId AND l.activity.active = true
            GROUP BY l.activity.id, l.activity.name, l.activity.category
            ORDER BY COUNT(l) DESC, MAX(l.startTime) DESC, l.activity.id ASC
            """)
    List<RecentActivityProjection> findMostLoggedActivities(@Param("userId") Long userId, Pageable pageable);
}
