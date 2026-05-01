package com.tracker.activity.service;

import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.AddActivityLogRequest;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.repository.ActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


@Service
public class ActivityLogService {
    @Autowired
    ActivityLogRepository activityLogRepository;

    @Autowired
    ActivityRepository activityRepository;

    public ResponseEntity<ActivityLogResponse> getActivityLogResponseEntity(Long id) {
        return ResponseEntity.ok(mapToActivityLogResponse(activityLogRepository.findById(id).orElseThrow(() -> new RuntimeException("No Activity Found"))));
    }

    public ResponseEntity<ActivityLogResponse> addActivityLogResponseResponseEntity(AddActivityLogRequest addActivityLogRequest) {
        ActivityLog activityLog = mapToActivityLog(addActivityLogRequest);
        activityLog.setDurationMinutes(Duration.between(activityLog.getStartTime(), activityLog.getEndTime()).toMinutes());
        activityLog.setXpEarned(activityLog.getDurationMinutes() * activityLog.getActivity().getXpMultiplier());
        activityLogRepository.save(activityLog);

        return ResponseEntity.ok(mapToActivityLogResponse(activityLog));
    }

    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(Long id) {
        List<ActivityLog> activityLogList = activityLogRepository.findByUserId(id);

        List<ActivityLogResponse> activityLogResponses = activityLogList.stream().map(this::mapToActivityLogResponse).toList();

        return ResponseEntity.ok(activityLogResponses);
    }

    private ActivityLog mapToActivityLog(AddActivityLogRequest addActivityLogRequest) {
        return ActivityLog.builder().userId(addActivityLogRequest.getUserId()).activity(activityRepository.findByName(addActivityLogRequest.getActivityName()).orElseThrow(() -> new RuntimeException("Activity not found!"))).startTime(addActivityLogRequest.getStartTime()).endTime(addActivityLogRequest.getEndTime()).notes(addActivityLogRequest.getNotes()).createdAt(LocalDateTime.now()).build();
    }

    private ActivityLogResponse mapToActivityLogResponse(ActivityLog activityLog) {
        return ActivityLogResponse.builder().id(activityLog.getId()).userId(activityLog.getUserId()).activity(activityLog.getActivity()).startTime(activityLog.getStartTime()).endTime(activityLog.getEndTime()).durationMinutes(activityLog.getDurationMinutes()).xpEarned(activityLog.getXpEarned()).notes(activityLog.getNotes()).createdAt(activityLog.getCreatedAt()).build();
    }
}
