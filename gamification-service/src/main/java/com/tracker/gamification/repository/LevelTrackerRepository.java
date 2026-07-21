package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dto.UserXpProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelTrackerRepository extends JpaRepository<LevelTracker, Long> {

    Optional<LevelTracker> findByUserIdAndActivityId(Long userId, Long activityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LevelTracker l WHERE l.userId = :userId AND l.activityId = :activityId")
    Optional<LevelTracker> findByUserIdAndActivityIdForUpdate(@Param("userId") Long userId,
                                                              @Param("activityId") Long activityId);


    List<LevelTracker> findAllByUserId(Long userId);

    List<LevelTracker> findAllByActivityId(Long activityId);

    @Query("""
            SELECT COALESCE(SUM(l.totalXp), 0)
            FROM LevelTracker l
            WHERE l.userId = :userId
            """)
    Double getTotalXpByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO level_tracker (user_id, activity_id, total_xp, current_level_xp)
            VALUES (:userId, :activityId, 0, 0)
            ON CONFLICT (user_id, activity_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("activityId") Long activityId);

    @Query("""
            SELECT l.userId as userId, SUM(l.totalXp) as totalXp
            FROM LevelTracker l
            GROUP BY l.userId
            ORDER BY SUM(l.totalXp) DESC
            """)
    List<UserXpProjection> findGlobalRanking(Pageable pageable);

    @Query("""
            SELECT l.userId as userId, l.totalXp as totalXp
            FROM LevelTracker l
            WHERE l.activityId = :activityId
            GROUP BY l.userId
            ORDER BY l.totalXp DESC
            """)
    List<UserXpProjection> findActivityRanking(@Param("activityId") Long activityId, Pageable pageable);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT l.user_id
                FROM level_tracker l
                GROUP BY l.user_id
                HAVING SUM(l.total_xp) > :xp
            ) ahead
            """, nativeQuery = true)
    long countUsersAhead(@Param("xp") double xp);
}
