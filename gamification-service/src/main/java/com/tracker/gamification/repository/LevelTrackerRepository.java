package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LevelTracker;
import jakarta.persistence.LockModeType;
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
}
