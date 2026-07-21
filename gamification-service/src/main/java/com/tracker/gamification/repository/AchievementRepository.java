package com.tracker.gamification.repository;

import com.tracker.gamification.dao.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    List<Achievement> findByActiveTrue();

    Optional<Achievement> findByCode(String code);
}
