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

    /** PUT: change the XP of an existing (activityId, level) row; 404 if absent, 409 if non-monotonic. */
    ActivityLevelThresholdDto updateActivityLevelThreshold(Long activityId, Integer level, double xpRequired);

    /** DELETE: remove one (activityId, level) row; 404 if absent. */
    void deleteActivityLevelThreshold(Long activityId, Integer level);
}
