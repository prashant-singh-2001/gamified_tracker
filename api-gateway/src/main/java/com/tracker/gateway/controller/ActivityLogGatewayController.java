package com.tracker.gateway.controller;


import com.tracker.gateway.client.ActivityClient;
import com.tracker.gateway.dto.ActivityLogResponse;
import com.tracker.gateway.dto.AddActivityLogRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activitylog")
@Validated
public class ActivityLogGatewayController {

    private final ActivityClient activityClient;

    public ActivityLogGatewayController(ActivityClient activityLogClient) {
        this.activityClient = activityLogClient;
    }


    @GetMapping("/{id}")
    public ResponseEntity<ActivityLogResponse> getActivity(@PathVariable @Positive(message = "id cannot be negative or zero") Long id) {
        return activityClient.getActivityLog(id);
    }

    @PostMapping
    public ResponseEntity<ActivityLogResponse> addActivity(@Valid @RequestBody AddActivityLogRequest request) {
        return activityClient.addActivityLog(request);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@PathVariable("id") @Positive(message = "id cannot be negative or zero") Long id) {
        return activityClient.getAllActivityForUser(id);
    }
}
