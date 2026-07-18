package com.tracker.activity.service.impl;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.service.ActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActivityServiceImpl implements ActivityService {

    private final ActivityRepository activityRepository;

    public ActivityServiceImpl(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public ResponseEntity<ActivityResponseRecord> getActivity(String name) {
        var activity = activityRepository.findByName(name)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + name));

        return ResponseEntity.ok(mapToResponse(activity));
    }

    @Override
    public ResponseEntity<ActivityResponseRecord> addActivityEntity(ActivityRequestRecord request) {
        var activity = mapToActivity(request);

        var savedActivity = activityRepository.save(activity);

        return ResponseEntity.ok(mapToResponse(savedActivity));
    }

    private Activity mapToActivity(ActivityRequestRecord activityRequest) {
        return Activity.builder()
                .name(activityRequest.name())
                .category(activityRequest.category())
                .description(activityRequest.description())
                .xpMultiplier(activityRequest.xpMultiplier())
                .active(activityRequest.active())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ActivityResponseRecord mapToResponse(Activity activity) {
        return new ActivityResponseRecord(
                activity.getName(),
                activity.getCategory(),
                // Report the multiplier that will actually apply (#10): the per-activity override,
                // or the Category base when there's no override — never a misleading 0.0.
                activity.effectiveXpMultiplier(),
                activity.isActive(),
                activity.getDescription(),
                activity.getCreatedAt()
        );
    }

    @Override
    public ResponseEntity<List<ActivityResponseRecord>> getAllActivities() {
        var activities = activityRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(activities);
    }
}
