package com.tracker.activity.service;

import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.StreakResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ActivityLogService {
    ResponseEntity<ActivityLogResponse> getActivityLogResponseEntity(Long id);

    ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(Long id);

    ResponseEntity<ActivityLogResponse> addActivityLogResponseResponseEntity(Long userId, ActivityLogRequest addActivityLogRequest);

    ResponseEntity<List<StreakResponse>> getStreaksForUser(Long userId);
}
