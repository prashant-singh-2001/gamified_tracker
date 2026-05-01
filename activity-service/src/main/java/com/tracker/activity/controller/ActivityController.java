package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityResponse;
import com.tracker.activity.dto.AddActivityRequest;
import com.tracker.activity.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Autowired
    ActivityService activityService;

    @GetMapping("/{name}")
    public ResponseEntity<ActivityResponse> getActivity(@PathVariable String name) {
        return activityService.getActivity(name);
    }

    @PostMapping("/")
    public ResponseEntity<ActivityResponse> addActivity(@RequestBody AddActivityRequest activityRequest) {
        return activityService.addAcitvityEntity(activityRequest);
    }
}
