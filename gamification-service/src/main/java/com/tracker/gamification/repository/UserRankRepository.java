package com.tracker.gamification.repository;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.dto.TierFloorProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRankRepository extends JpaRepository<UserRank, Long> {
    Optional<UserRank> findByUserId(Long userId);

    // #85: both queries below are tier-scoped against a table that carried only its user_id
    // primary key. countByTier is called once PER TIER (9x) by RankServiceImpl.getDistribution --
    // that fanout is exactly what idx_user_rank_tier_total_xp (V6) exists to stop from being 9
    // full scans on every GET /ranks request.
    List<UserRank> findByTierOrderByTotalXpDesc(RankTier tier, Pageable pageable);

    long countByTier(RankTier tier);

    // One row per POPULATED tier: its lowest XP. Backs the rank nudge on GET /ranks/me. An empty
    // tier simply has no row, which is how the service knows to skip it. Covered by an index-only
    // scan of idx_user_rank_tier_total_xp (V6); at most nine rows come back.
    @Query("SELECT r.tier AS tier, MIN(r.totalXp) AS minTotalXp FROM UserRank r GROUP BY r.tier")
    List<TierFloorProjection> findTierFloors();


    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO {h-schema}user_rank (user_id, total_xp, overall_level, tier, percentile, position, total_users, updated_at)
            VALUES (:userId, :totalXp, :overallLevel, :tier, :percentile, :position, :totalUsers, now())
            ON CONFLICT (user_id) DO UPDATE SET
                total_xp = EXCLUDED.total_xp,
                overall_level = EXCLUDED.overall_level,
                tier = EXCLUDED.tier,
                percentile = EXCLUDED.percentile,
                position = EXCLUDED.position,
                total_users = EXCLUDED.total_users,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsert(@Param("userId") Long userId,
               @Param("totalXp") double totalXp,
               @Param("overallLevel") int overallLevel,
               @Param("tier") String tier,
               @Param("percentile") double percentile,
               @Param("position") int position,
               @Param("totalUsers") int totalUsers);
}
