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

    // #85: user-scoped lookups (this one, findAllByUserId, getTotalXpByUserId below) ride the
    // leading user_id column of uk_level_tracker_user_activity -- no separate index needed.
    Optional<LevelTracker> findByUserIdAndActivityId(Long userId, Long activityId);

    // #77/#88: ownership-scoped lookup for GET /level/{id} -- a miss here (wrong owner or no
    // such row) is intentionally indistinguishable from a genuinely missing id, so the handler
    // renders the same 404 either way instead of leaking existence via a 403.
    Optional<LevelTracker> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LevelTracker l WHERE l.userId = :userId AND l.activityId = :activityId")
    Optional<LevelTracker> findByUserIdAndActivityIdForUpdate(@Param("userId") Long userId,
                                                              @Param("activityId") Long activityId);


    List<LevelTracker> findAllByUserId(Long userId);

    // #85: activity_id ALONE, unlike the userId-scoped finders above -- uk_level_tracker_user_activity
    // can't serve this, activity_id is its trailing column, not leading. Backed by
    // idx_level_tracker_activity_id (V6). Same story for findActivityRanking below.
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
    // #85: WHERE l.activityId = :activityId -- served by idx_level_tracker_activity_id, see
    // findAllByActivityId above.
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

    
    @Query(value = """
            SELECT l.userId AS userId, 
            SUM(l.totalXp) AS totalXp 
            FROM LevelTracker l 
            GROUP BY l.userId 
            ORDER BY SUM(l.totalXp) DESC
            """)
    List<UserXpProjection> findAllUserTotals();
}
