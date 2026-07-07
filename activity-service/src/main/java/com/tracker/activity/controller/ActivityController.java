package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.dto.AddActivityRequestRecord;
import com.tracker.activity.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Autowired
    ActivityService activityService;

    @GetMapping("/{name}")
    public ResponseEntity<ActivityResponseRecord> getActivity(@PathVariable String name) {
        return activityService.getActivity(name);
    }

    @GetMapping("/")
    public ResponseEntity<List<ActivityResponseRecord>> getAllActivities() {
        return activityService.getAllActivities();
    }

    @PostMapping("/")
    public ResponseEntity<ActivityResponseRecord> addActivity(@RequestBody AddActivityRequestRecord activityRequest) {
        return activityService.addAcitvityEntity(activityRequest);
    }
}
