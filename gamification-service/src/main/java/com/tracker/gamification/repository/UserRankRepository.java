package com.tracker.gamification.repository;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.dto.DivisionFloorProjection;
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
    // that fanout is exactly what the tier indexes (V6, and V8 for the score-ordered board) exist to stop from being 9
    // full scans on every GET /ranks request.
    List<UserRank> findByTierOrderByRankScoreDesc(RankTier tier, Pageable pageable);

    long countByTier(RankTier tier);

    // One row per POPULATED tier: its lowest rank score. Backs the rank nudge on GET /ranks/me. An empty
    // tier simply has no row, which is how the service knows to skip it. Covered by an index-only
    // scan of idx_user_rank_tier_score (V8); at most nine rows come back.
    @Query("SELECT r.tier AS tier, MIN(r.rankScore) AS minScore FROM UserRank r GROUP BY r.tier")
    List<TierFloorProjection> findTierFloors();

    // One row per POPULATED (tier, division): its lowest score. Backs the division nudge on
    // GET /ranks/me. At most 27 rows.
    @Query("SELECT r.tier AS tier, r.division AS division, MIN(r.rankScore) AS minScore "
            + "FROM UserRank r GROUP BY r.tier, r.division")
    List<DivisionFloorProjection> findDivisionFloors();

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO {h-schema}user_rank (user_id, total_xp, overall_level, tier, percentile, position,
                                             total_users, updated_at, rank_score, division,
                                             previous_tier, previous_division, previous_position, previous_at)
            VALUES (:userId, :totalXp, :overallLevel, :tier, :percentile, :position,
                    :totalUsers, now(), :rankScore, :division,
                    :previousTier, :previousDivision, :previousPosition, :previousAt)
            ON CONFLICT (user_id) DO UPDATE SET
                total_xp = EXCLUDED.total_xp,
                overall_level = EXCLUDED.overall_level,
                tier = EXCLUDED.tier,
                percentile = EXCLUDED.percentile,
                position = EXCLUDED.position,
                total_users = EXCLUDED.total_users,
                updated_at = EXCLUDED.updated_at,
                rank_score = EXCLUDED.rank_score,
                division = EXCLUDED.division,
                previous_tier = EXCLUDED.previous_tier,
                previous_division = EXCLUDED.previous_division,
                previous_position = EXCLUDED.previous_position,
                previous_at = EXCLUDED.previous_at
            """, nativeQuery = true)
    int upsert(@Param("userId") Long userId,
               @Param("totalXp") double totalXp,
               @Param("overallLevel") int overallLevel,
               @Param("tier") String tier,
               @Param("percentile") double percentile,
               @Param("position") int position,
               @Param("totalUsers") int totalUsers,
               @Param("rankScore") double rankScore,
               @Param("division") int division,
               @Param("previousTier") String previousTier,
               @Param("previousDivision") Integer previousDivision,
               @Param("previousPosition") Integer previousPosition,
               @Param("previousAt") java.time.LocalDateTime previousAt);
}
