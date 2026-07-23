package com.tracker.gateway.controller;

import com.tracker.gateway.client.ActivityClient;
import com.tracker.gateway.dto.ActivityResponse;
import com.tracker.gateway.dto.AddActivityRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
@Validated
public class ActivityGatewayController {

    private final ActivityClient activityClient;

    public ActivityGatewayController(ActivityClient activityClient) {
        this.activityClient = activityClient;
    }

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> getAllActivities() {
        return activityClient.getAllActivities();
    }

    @GetMapping("/{name}")
    public ResponseEntity<ActivityResponse> getActivity(@PathVariable @NotBlank(message = "name is required") String name) {
        return activityClient.getActivity(name);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ActivityResponse> addActivity(@Valid @RequestBody AddActivityRequest request) {
        return activityClient.addActivity(request);
    }
}
