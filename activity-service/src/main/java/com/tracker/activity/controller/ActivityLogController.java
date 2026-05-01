package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.AddActivityLogRequest;
import com.tracker.activity.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activitylog")
public class ActivityLogController {

    @Autowired
    ActivityLogService activityLogService;

    @GetMapping("/{id}")
    public ResponseEntity<ActivityLogResponse> getActivityLog(@PathVariable("id") Long id) {
        return activityLogService.getActivityLogResponseEntity(id);
    }

    @PostMapping("/")
    public ResponseEntity<ActivityLogResponse> addActivityLog(@RequestBody AddActivityLogRequest addActivityLogRequest) {
        return activityLogService.addActivityLogResponseResponseEntity(addActivityLogRequest);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@PathVariable("id") Long id) {
        return activityLogService.getAllActivityForUser(id);
    }
}
