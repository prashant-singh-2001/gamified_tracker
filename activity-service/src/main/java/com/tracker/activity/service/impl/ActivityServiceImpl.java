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
        // #84: an inactive activity is indistinguishable from a missing one on a read -- that's
        // what "logically invisible" means for a soft delete, and it avoids leaking catalog
        // existence. Do NOT switch this to findByName + an isActive() check; the whole point is
        // that findByName stays unfiltered for the other consumers that depend on it (see
        // ActivityRepository).
        var activity = activityRepository.findByNameAndActiveTrue(name)
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
                // #84: active is now Boolean, not boolean -- omitting the field from the request
                // JSON must mean "active" (the ordinary case), not "silently create an invisible
                // activity nobody can ever see again" (no PUT/PATCH/DELETE exists to fix it).
                .active(activityRequest.active() == null || activityRequest.active())
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
        // #84: same reasoning as getActivity above -- inactive activities are logically deleted,
        // so the catalog listing must not include them.
        var activities = activityRepository.findAllByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(activities);
    }
}
