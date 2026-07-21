package com.tracker.gamification.repository;

import com.tracker.gamification.dao.OverallLevelThreshold;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OverallLevelThresholdRepository extends JpaRepository<OverallLevelThreshold, Integer> {
    Optional<Integer> findReachedLevel(double xp, Pageable pageable);
}
