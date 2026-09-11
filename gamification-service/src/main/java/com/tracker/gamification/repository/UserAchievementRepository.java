package com.tracker.gamification.repository;

import com.tracker.gamification.dao.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    // #85: deliberately NOT indexed. uk_user_achievement UNIQUE(user_id, achievement_id) already
    // has user_id as its leading column, so a plain (user_id) index would be redundant with it --
    // and both finders below have zero callers: the whole path sits behind
    // AchievementServiceImpl.evaluateAndAward, which no production code calls (no
    // AchievementController exists). See docs/features/achievement-badges.md.
    List<UserAchievement> findByUserIdOrderByUnlockedAtDesc(Long userId);

    boolean existsByUserIdAndAchievementId(Long userId, Long achievementId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_achievement (user_id, achievement_id, unlocked_at)
            VALUES (:userId, :achievementId, now())
            ON CONFLICT (user_id, achievement_id) DO NOTHING
            """, nativeQuery = true)
    int grantIfAbsent(@Param("userId") Long userId, @Param("achievementId") Long achievementId);
}
