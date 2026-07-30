package com.tracker.gamification.repository;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ActivityLevelThresholdRepository extends JpaRepository<ActivityLevelThreshold, ActivityLevelThresholdId> {

    @Query("""
            SELECT a
            FROM ActivityLevelThreshold a
            WHERE a.id.activityId = :activityId
            AND a.xpRequired <= :xp
            ORDER BY a.id.level DESC
            """)
    List<ActivityLevelThreshold> findReachedLevels(
            @Param("activityId") Long activityId,
            @Param("xp") double xp,
            Pageable pageable
    );

    // Mirror of findReachedLevels: the LOWEST level whose xpRequired is still ahead of the user.
    @Query("""
            SELECT a
            FROM ActivityLevelThreshold a
            WHERE a.id.activityId = :activityId
            AND a.xpRequired > :xp
            ORDER BY a.id.level ASC
            """)
    List<ActivityLevelThreshold> findNextLevels(
            @Param("activityId") Long activityId,
            @Param("xp") double xp,
            Pageable pageable
    );

    // Batch form for list endpoints: one query for every activity in the result set instead of N.
    @Query("""
            SELECT a
            FROM ActivityLevelThreshold a
            WHERE a.id.activityId IN :activityIds
            ORDER BY a.id.activityId ASC, a.id.level ASC
            """)
    List<ActivityLevelThreshold> findAllForActivities(@Param("activityIds") Collection<Long> activityIds);

    // Precedence guard for the default level curve: an activity with ANY explicit row uses only
    // its own rows, never the formula fallback (LevelTrackerServiceImpl.resolveLevel).
    @Query("""
            SELECT COUNT(a)
            FROM ActivityLevelThreshold a
            WHERE a.id.activityId = :activityId
            """)
    long countForActivity(@Param("activityId") Long activityId);

    @Query("""
            SELECT a
            FROM ActivityLevelThreshold a
            WHERE a.id.activityId = :activityId
            ORDER BY a.id.level ASC
            """)
    List<ActivityLevelThreshold> findAllForActivity(@Param("activityId") Long activityId);
}
