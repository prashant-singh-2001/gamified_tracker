package com.tracker.gamification.service;

import com.tracker.gamification.dto.ActivityLevelThresholdDto;

import java.util.List;

public interface ActivityLevelThresholdService {

    ActivityLevelThresholdDto getActivityLevelThresholdById(
            ActivityLevelThresholdDto activityLevelThresholdDto);

    ActivityLevelThresholdDto saveActivityLevelThreshold(
            ActivityLevelThresholdDto activityLevelThresholdDto);

    List<ActivityLevelThresholdDto> getAllActivityLevelThreshold();

    // Explicit rows when the activity has any; otherwise the generated default curve, up to
    // upToLevel — not persisted. Makes the fallback curve visible instead of implicit.
    List<ActivityLevelThresholdDto> getEffectiveThresholds(Long activityId, int upToLevel);
}
