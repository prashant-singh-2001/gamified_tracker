package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.service.ActivityLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activitylog")
@Validated
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityLogResponse> getActivityLog(@PathVariable("id") @Positive(message = "id cannot be negative or zero") Long id) {
        return activityLogService.getActivityLogResponseEntity(id);
    }

    @PostMapping("/")
    public ResponseEntity<ActivityLogResponse> addActivityLog(@Valid @RequestBody ActivityLogRequest activityLogRequest) {
        return activityLogService.addActivityLogResponseResponseEntity(activityLogRequest);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@PathVariable("id") @Positive(message = "id cannot be negative or zero") Long id) {
        return activityLogService.getAllActivityForUser(id);
    }
}
