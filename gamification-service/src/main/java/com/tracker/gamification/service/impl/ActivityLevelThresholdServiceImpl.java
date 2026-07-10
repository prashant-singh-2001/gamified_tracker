package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.dto.ActivityLevelThresholdDto;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.service.ActivityLevelThresholdService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ActivityLevelThresholdServiceImpl implements ActivityLevelThresholdService {

    private final ActivityLevelThresholdRepository activityLevelThresholdRepository;

    public ActivityLevelThresholdServiceImpl(ActivityLevelThresholdRepository activityLevelThresholdRepository) {
        this.activityLevelThresholdRepository = activityLevelThresholdRepository;
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
}
