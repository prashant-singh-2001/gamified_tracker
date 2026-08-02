package com.tracker.gamification.service;

import com.tracker.gamification.dao.Achievement;
import com.tracker.gamification.dto.UserAchievementDto;

import java.util.List;

public interface AchievementService {

    List<Achievement> evaluateAndAward(Long userId);

    /** Every badge this user has unlocked, newest first. */
    List<UserAchievementDto> findUnlocked(Long userId);
}
