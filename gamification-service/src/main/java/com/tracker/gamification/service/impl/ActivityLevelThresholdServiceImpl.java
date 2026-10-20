package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.domain.LevelCurve;
import com.tracker.gamification.dto.ActivityLevelThresholdDto;
import com.tracker.gamification.exception.InvalidThresholdCurveException;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.service.ActivityLevelThresholdService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public ActivityLevelThresholdDto saveActivityLevelThreshold(ActivityLevelThresholdDto activityLevelThresholdDto) {
        // POST is also an upsert (same activityId+level replaces the row), so it needs the same curve
        // guard as PUT -- otherwise the invariant PUT enforces could be bypassed through POST.
        requireMonotonic(activityLevelThresholdDto.activityId(), activityLevelThresholdDto.level(),
                activityLevelThresholdDto.xpRequired());
        var activityLevelThreshold = mapToEntity(activityLevelThresholdDto);
        activityLevelThresholdRepository.save(activityLevelThreshold);
        return mapToDto(activityLevelThreshold);
    }

    // Existing trackers are NOT recomputed here. level_tracker.level/current_level_xp are stored and are
    // re-resolved from total_xp on that user's next XP award (LevelTrackerServiceImpl.applyLevel), so an
    // edit shows up lazily, per (user, activity), and no past level-up event is rewritten.
    @Override
    @Transactional
    public ActivityLevelThresholdDto updateActivityLevelThreshold(Long activityId, Integer level, double xpRequired) {
        var existing = activityLevelThresholdRepository.findById(new ActivityLevelThresholdId(activityId, level))
                .orElseThrow(() -> new NoSuchElementException(
                        "ActivityLevelThreshold not found for activity " + activityId + " level " + level));
        requireMonotonic(activityId, level, xpRequired);
        existing.setXpRequired(xpRequired);
        return mapToDto(activityLevelThresholdRepository.save(existing));
    }

    // Removing a row can leave a gap in the level numbers (levels 1,3). That is still a valid curve:
    // level resolution takes the highest level whose xpRequired has been reached, so 3 simply follows
    // 1. Removing an activity's LAST row hands it back to the default curve (see resolveLevel).
    @Override
    @Transactional
    public void deleteActivityLevelThreshold(Long activityId, Integer level) {
        var id = new ActivityLevelThresholdId(activityId, level);
        if (!activityLevelThresholdRepository.existsById(id)) {
            throw new NoSuchElementException(
                    "ActivityLevelThreshold not found for activity " + activityId + " level " + level);
        }
        activityLevelThresholdRepository.deleteById(id);
    }

    // Cumulative XP must rise strictly with level: below the new row's level the nearest row needs less,
    // above it the nearest row needs more. Compared against the row's neighbours only, which is enough
    // because the rows already stored are themselves monotonic.
    private void requireMonotonic(Long activityId, Integer level, double xpRequired) {
        ActivityLevelThreshold below = null;
        ActivityLevelThreshold above = null;
        for (var row : activityLevelThresholdRepository.findAllForActivity(activityId)) {
            int rowLevel = row.getId().getLevel();
            if (rowLevel < level) {
                below = row;
            } else if (rowLevel > level && above == null) {
                above = row;
            }
        }
        if (below != null && xpRequired <= below.getXpRequired()) {
            throw new InvalidThresholdCurveException("Level " + level + " must need more XP than level "
                    + below.getId().getLevel() + " (" + below.getXpRequired() + "), but got " + xpRequired);
        }
        if (above != null && xpRequired >= above.getXpRequired()) {
            throw new InvalidThresholdCurveException("Level " + level + " must need less XP than level "
                    + above.getId().getLevel() + " (" + above.getXpRequired() + "), but got " + xpRequired);
        }
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
