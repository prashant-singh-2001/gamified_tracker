package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.service.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;


import lombok.AllArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/activity")
@Validated
public class ActivityController {

    private final ActivityService activityService;


    @GetMapping("/{name}")
    public ResponseEntity<ActivityResponseRecord> getActivity(@PathVariable @NotBlank(message = "name is required") String name) {
        return activityService.getActivity(name);
    }

    @GetMapping("/")
    public ResponseEntity<List<ActivityResponseRecord>> getAllActivities() {
        return activityService.getAllActivities();
    }

    @PostMapping("/")
    public ResponseEntity<ActivityResponseRecord> addActivity(@Valid @RequestBody ActivityRequestRecord activityRequest) {
        return activityService.addActivityEntity(activityRequest);
    }
}
