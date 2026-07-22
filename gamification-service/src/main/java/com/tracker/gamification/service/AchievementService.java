package com.tracker.gamification.service;

import com.tracker.gamification.dao.Achievement;

import java.util.List;

public interface AchievementService {

    List<Achievement> evaluateAndAward(Long userId);
}
