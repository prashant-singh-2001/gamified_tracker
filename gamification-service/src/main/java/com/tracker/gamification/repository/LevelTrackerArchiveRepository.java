package com.tracker.gamification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tracker.gamification.dao.LevelTrackerArchive;

public interface LevelTrackerArchiveRepository extends JpaRepository<LevelTrackerArchive, Long> {

    // #85: deliberately NOT indexed, unlike the other tables touched by that issue. This finder
    // has zero callers anywhere in src/main or src/test, while archivePreviousState (see
    // LevelTrackerServiceImpl) writes a row here on every non-first XP award -- the hottest write
    // path in the service. An index would be pure write cost for a read nothing performs. If this
    // finder is ever wired up, the shape to add is (user_id, activity_id, archived_at DESC) --
    // bare (user_id) wouldn't even fully serve it.
    List<LevelTrackerArchive> findByUserIdAndActivityIdOrderByArchivedAtDesc(Long userId, Long activityId);
}
