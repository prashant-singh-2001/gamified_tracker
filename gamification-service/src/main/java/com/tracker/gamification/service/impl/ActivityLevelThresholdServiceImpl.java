package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.domain.LevelCurve;
import com.tracker.gamification.dto.ActivityLevelThresholdDto;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.service.ActivityLevelThresholdService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ActivityLevelThresholdServiceImpl implements ActivityLevelThresholdService {

    private final ActivityLevelThresholdRepository activityLevelThresholdRepository;
    private final LevelCurve levelCurve;

    public ActivityLevelThresholdServiceImpl(ActivityLevelThresholdRepository activityLevelThresholdRepository,
                                              LevelCurve levelCurve) {
        this.activityLevelThresholdRepository = activityLevelThresholdRepository;
        this.levelCurve = levelCurve;
    }

    @Override
    public ActivityLevelThresholdDto getActivityLevelThresholdById(ActivityLevelThresholdDto activityLevelThresholdDto) {
        var activityLevelThreshold = activityLevelThresholdRepository.findById(mapToEntity(activityLevelThresholdDto).getId());
        if (activityLevelThreshold.isPresent()) {
            return mapToDto(activityLevelThreshold.get());
        } else throw new NoSuchElementException("ActivityLevelThreshold not found");
    }

    @Override
    public ActivityLevelThresholdDto saveActivityLevelThreshold(ActivityLevelThresholdDto activityLevelThresholdDto) {
        var activityLevelThreshold = mapToEntity(activityLevelThresholdDto);
        activityLevelThresholdRepository.save(activityLevelThreshold);
        return mapToDto(activityLevelThreshold);
    }

    public ActivityLevelThreshold mapToEntity(ActivityLevelThresholdDto dto) {

        return ActivityLevelThreshold.builder()
                .id(
                        ActivityLevelThresholdId.builder()
                                .activityId(dto.activityId())
                                .level(dto.level())
                                .build()
                )
                .xpRequired(dto.xpRequired())
                .build();
    }

    public ActivityLevelThresholdDto mapToDto(ActivityLevelThreshold entity) {

        return new ActivityLevelThresholdDto(
                entity.getId().getActivityId(),
                entity.getId().getLevel(),
                entity.getXpRequired()
        );
    }

    @Override
    public List<ActivityLevelThresholdDto> getAllActivityLevelThreshold() {

        return activityLevelThresholdRepository.findAll().stream().map(this::mapToDto).toList();
    }

    @Override
    public List<ActivityLevelThresholdDto> getEffectiveThresholds(Long activityId, int upToLevel) {
        var explicit = activityLevelThresholdRepository.findAllForActivity(activityId);
        if (!explicit.isEmpty()) {
            return explicit.stream().map(this::mapToDto).toList();
        }

        // No explicit rows — surface the same default curve LevelTrackerServiceImpl.resolveLevel
        // would fall back to, so it's visible to a client instead of only implicit in level math.
        List<ActivityLevelThresholdDto> generated = new ArrayList<>();
        for (int level = 1; level <= upToLevel; level++) {
            generated.add(new ActivityLevelThresholdDto(activityId, level, levelCurve.xpRequiredFor(level)));
        }
        return generated;
    }
}
