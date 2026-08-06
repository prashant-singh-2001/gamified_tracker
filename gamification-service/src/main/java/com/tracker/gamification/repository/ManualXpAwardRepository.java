package com.tracker.gamification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tracker.gamification.dao.ManualXpAward;

public interface ManualXpAwardRepository extends JpaRepository<ManualXpAward, Long> {
}
