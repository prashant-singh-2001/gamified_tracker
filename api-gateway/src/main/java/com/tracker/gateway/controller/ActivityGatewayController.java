package com.tracker.gateway.controller;

import com.tracker.gateway.client.ActivityClient;
import com.tracker.gateway.dto.ActivityResponse;
import com.tracker.gateway.dto.AddActivityRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activity")
public class ActivityGatewayController {

    private final ActivityClient activityClient;

    public ActivityGatewayController(ActivityClient activityClient) {
        this.activityClient = activityClient;
    }

    @GetMapping("/{name}")
    public ResponseEntity<ActivityResponse> getActivity(@PathVariable String name) {
        return activityClient.getActivity(name);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ActivityResponse> addActivity(@RequestBody AddActivityRequest request) {
        return activityClient.addActivity(request);
    }
}
