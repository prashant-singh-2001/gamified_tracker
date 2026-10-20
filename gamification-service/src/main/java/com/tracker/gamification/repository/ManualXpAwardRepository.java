package com.tracker.gamification.repository;

import com.tracker.gamification.dao.ManualXpAward;
import com.tracker.gamification.dto.TimedXpProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ManualXpAwardRepository extends JpaRepository<ManualXpAward, Long> {

    // Rank scoring input: an admin award counts for its TARGET user from the moment it was made,
    // exactly like XP earned by logging.
    @Query("SELECT m.targetUserId AS userId, m.xp AS xp, m.awardedAt AS occurredAt "
            + "FROM ManualXpAward m WHERE m.awardedAt >= :cutoff")
    List<TimedXpProjection> findAwardsSince(@Param("cutoff") LocalDateTime cutoff);
}
