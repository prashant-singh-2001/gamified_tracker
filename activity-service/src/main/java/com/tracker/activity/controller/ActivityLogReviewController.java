package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.ReviewQueueItemResponse;
import com.tracker.activity.service.ActivityLogReviewService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin review queue for session-integrity (#67) quarantined logs. Deliberately mounted under
 * {@code /activitylog/review/**} so it rides the gateway's existing {@code /api/activitylog/**}
 * route/rate-limit bucket instead of needing a new one. Authorization (ROLE_ADMIN) is enforced at
 * the gateway — this service has no Spring Security of its own.
 */
@AllArgsConstructor
@RestController
@RequestMapping("/activitylog/review")
public class ActivityLogReviewController {

    private final ActivityLogReviewService activityLogReviewService;

    @GetMapping("/flagged")
    public ResponseEntity<List<ReviewQueueItemResponse>> getFlaggedLogs() {
        return activityLogReviewService.getFlaggedLogs();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ActivityLogResponse> approve(@PathVariable("id") Long id) {
        return activityLogReviewService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ActivityLogResponse> reject(@PathVariable("id") Long id) {
        return activityLogReviewService.reject(id);
    }
}
