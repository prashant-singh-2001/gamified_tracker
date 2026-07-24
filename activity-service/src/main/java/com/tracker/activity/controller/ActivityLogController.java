package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.StreakResponse;
import com.tracker.activity.service.ActivityLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/activitylog")
@Validated
public class ActivityLogController {

    private final ActivityLogService activityLogService;


    @GetMapping("/{id}")
    public ResponseEntity<ActivityLogResponse> getActivityLog(@PathVariable("id") @Positive(message = "id cannot be negative or zero") Long id) {
        return activityLogService.getActivityLogResponseEntity(id);
    }

    @PostMapping("/")
    public ResponseEntity<ActivityLogResponse> addActivityLog(@RequestHeader("userId") @Positive(message = "id cannot be negative or zero") Long userId, @Valid @RequestBody ActivityLogRequest activityLogRequest) {
        return activityLogService.addActivityLogResponseResponseEntity(userId, activityLogRequest);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@PathVariable("id") @Positive(message = "id cannot be negative or zero") Long id) {
        return activityLogService.getAllActivityForUser(id);
    }

    @GetMapping("/streaks/user/{id}")
    public ResponseEntity<List<StreakResponse>> getAllStreaksForUser(@PathVariable("id") Long id) {
        return activityLogService.getStreaksForUser(id);
    }
}
