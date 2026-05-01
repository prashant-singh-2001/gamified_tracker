package com.tracker.gateway.controller;


import com.tracker.gateway.client.ActivityClient;
import com.tracker.gateway.dto.ActivityLogResponse;
import com.tracker.gateway.dto.AddActivityLogRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activitylog")
public class ActivityLogGatewayController {

    private final ActivityClient activityClient;

    public ActivityLogGatewayController(ActivityClient activityLogClient) {
        this.activityClient = activityLogClient;
    }


    @GetMapping("/{id}")
    public ResponseEntity<ActivityLogResponse> getActivity(@PathVariable Long id) {
        return activityClient.getActivityLog(id);
    }

    @PostMapping
    public ResponseEntity<ActivityLogResponse> addActivity(@RequestBody AddActivityLogRequest request) {
        return activityClient.addActivityLog(request);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@PathVariable("id") Long id) {
        return activityClient.getAllActivityForUser(id);
    }
}
