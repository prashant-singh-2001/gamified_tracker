package com.tracker.gamification.repository;

import com.tracker.gamification.dao.OverallLevelThreshold;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OverallLevelThresholdRepository extends JpaRepository<OverallLevelThreshold, Integer> {

    @Query("""
            SELECT o.level
            FROM OverallLevelThreshold o
            WHERE o.threshold <= :xp
            ORDER BY o.level DESC
            """)
    List<Integer> findReachedLevel(@Param("xp") double xp, Pageable pageable);
}
