package com.tracker.gamification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tracker.gamification.dao.LevelTrackerArchive;

public interface LevelTrackerArchiveRepository extends JpaRepository<LevelTrackerArchive, Long> {

    List<LevelTrackerArchive> findByUserIdAndActivityIdOrderByArchivedAtDesc(Long userId, Long activityId);
}
