package com.tracker.activity.service;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dto.ActivityResponse;
import com.tracker.activity.dto.AddActivityRequest;
import com.tracker.activity.repository.ActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ActivityService {

    @Autowired
    ActivityRepository activityRepository;

    public ResponseEntity<ActivityResponse> getActivity(String name) {
        Activity activity = activityRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + name));

        return ResponseEntity.ok(mapToResponse(activity));
    }

    public ResponseEntity<ActivityResponse> addAcitvityEntity(AddActivityRequest request) {
        Activity activity = mapToActivity(request);

        Activity savedActivity = activityRepository.save(activity);

        return ResponseEntity.ok(mapToResponse(savedActivity));
    }

    private Activity mapToActivity(AddActivityRequest activityRequest) {
        return Activity.builder().name(activityRequest.getName()).category(activityRequest.getCategory()).description(activityRequest.getDescription()).xpMultiplier(activityRequest.getXpMultiplier()).active(activityRequest.isActive()).createdAt(LocalDateTime.now()).build();
    }

    private ActivityResponse mapToResponse(Activity activity) {
        return ActivityResponse.builder().name(activity.getName()).category(activity.getCategory()).xpMultiplier(activity.getXpMultiplier()).description(activity.getDescription()).createdAt(activity.getCreatedAt()).build();
    }
}
