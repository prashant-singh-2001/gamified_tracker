package com.tracker.activity.service;

import com.tracker.activity.dto.ActivityDeleteResponse;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.dto.ActivityUpdateRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ActivityService {
    ResponseEntity<ActivityResponseRecord> getActivity(String name);

    /** GET /activity/{id}: {@code digits} is tried as an id first, then as an activity name. */
    ResponseEntity<ActivityResponseRecord> getActivityByIdOrName(String digits);

    ResponseEntity<List<ActivityResponseRecord>> getAllActivities();

    /** Admin variants: with {@code includeInactive} the soft-delete filter is not applied. */
    ResponseEntity<ActivityResponseRecord> getActivity(String name, boolean includeInactive);

    ResponseEntity<ActivityResponseRecord> getActivityByIdOrName(String digits, boolean includeInactive);

    ResponseEntity<List<ActivityResponseRecord>> getAllActivities(boolean includeInactive);

    ResponseEntity<ActivityResponseRecord> addActivityEntity(ActivityRequestRecord request);

    /** PATCH /activity/{id}: partial update; only non-null fields change. */
    ResponseEntity<ActivityResponseRecord> updateActivity(Long id, ActivityUpdateRequest request);

    /** DELETE /activity/{id}: soft delete while logs or streaks reference it, hard delete otherwise. */
    ResponseEntity<ActivityDeleteResponse> deleteActivity(Long id);
}
