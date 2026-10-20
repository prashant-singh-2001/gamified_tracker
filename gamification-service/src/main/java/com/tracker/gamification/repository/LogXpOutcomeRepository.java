package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LogXpOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LogXpOutcomeRepository extends JpaRepository<LogXpOutcome, Long> {

    // Ownership is part of the lookup, not a separate check: someone else's log and a log that
    // doesn't exist are indistinguishable to the caller (same as findByIdAndUserId elsewhere).
    Optional<LogXpOutcome> findByLogIdAndUserId(Long logId, Long userId);
}
