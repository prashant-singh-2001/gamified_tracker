package com.tracker.activity.repository;

import com.tracker.activity.dao.ActivityStreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityStreakRepository extends JpaRepository<ActivityStreak, Long> {

    Optional<ActivityStreak> findByUserIdAndActivityId(Long userId, Long activityId);

    List<ActivityStreak> findByUserId(Long userId);

}
