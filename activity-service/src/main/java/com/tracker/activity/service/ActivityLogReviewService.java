package com.tracker.activity.service;

import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.ReviewQueueItemResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ActivityLogReviewService {
    ResponseEntity<List<ReviewQueueItemResponse>> getFlaggedLogs();

    ResponseEntity<ActivityLogResponse> approve(Long logId);

    ResponseEntity<ActivityLogResponse> reject(Long logId);
}
