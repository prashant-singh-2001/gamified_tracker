package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LogXpOutcome;
import com.tracker.gamification.dto.TimedXpProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

import java.util.Optional;

public interface LogXpOutcomeRepository extends JpaRepository<LogXpOutcome, Long> {

    // Ownership is part of the lookup, not a separate check: someone else's log and a log that
    // doesn't exist are indistinguishable to the caller (same as findByIdAndUserId elsewhere).
    Optional<LogXpOutcome> findByLogIdAndUserId(Long logId, Long userId);

    // Rank scoring input: every award applied for a session at or after the cutoff. The index on
    // occurred_at (V8) keeps this to the recent slice rather than the whole history.
    @Query("SELECT o.userId AS userId, o.xpApplied AS xp, o.occurredAt AS occurredAt "
            + "FROM LogXpOutcome o WHERE o.occurredAt >= :cutoff")
    List<TimedXpProjection> findAwardsSince(@Param("cutoff") LocalDateTime cutoff);
}
