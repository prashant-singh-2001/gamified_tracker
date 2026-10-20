package com.tracker.gamification.controller;

import com.tracker.gamification.dto.ActivityLevelThresholdDto;
import com.tracker.gamification.dto.ActivityLevelThresholdUpdateRequest;
import com.tracker.gamification.service.impl.ActivityLevelThresholdServiceImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/threshold")
public class ActivityLevelThresholdController {
    @Autowired
    private ActivityLevelThresholdServiceImpl activityLevelThresholdService;

    @GetMapping
    public ResponseEntity<List<ActivityLevelThresholdDto>> getActivityLevelThreshold() {
        return ResponseEntity.ok(activityLevelThresholdService.getAllActivityLevelThreshold());
    }

    @PostMapping("/activity")
    public ResponseEntity<ActivityLevelThresholdDto> getActivityLevelThresholdById(@Valid @RequestBody ActivityLevelThresholdDto activityLevelThresholdDto) {
        return ResponseEntity.ok(activityLevelThresholdService.getActivityLevelThresholdById(activityLevelThresholdDto));
    }

    @PostMapping
    public ResponseEntity<ActivityLevelThresholdDto> createActivityLevelThreshold(@Valid @RequestBody ActivityLevelThresholdDto activityLevelThresholdDto) {
        return ResponseEntity.ok(activityLevelThresholdService.saveActivityLevelThreshold(activityLevelThresholdDto));
    }

    // ADMIN only (gateway). A threshold has no surrogate id -- the row is identified by its
    // (activityId, level) pair, so that is the path.
    @PutMapping("/{activityId}/{level}")
    public ResponseEntity<ActivityLevelThresholdDto> updateActivityLevelThreshold(
            @PathVariable("activityId") Long activityId,
            @PathVariable("level") Integer level,
            @Valid @RequestBody ActivityLevelThresholdUpdateRequest request) {
        return ResponseEntity.ok(activityLevelThresholdService.updateActivityLevelThreshold(activityId, level, request.xpRequired()));
    }

    @DeleteMapping("/{activityId}/{level}")
    public ResponseEntity<Void> deleteActivityLevelThreshold(
            @PathVariable("activityId") Long activityId,
            @PathVariable("level") Integer level) {
        activityLevelThresholdService.deleteActivityLevelThreshold(activityId, level);
        return ResponseEntity.noContent().build();
    }

    // Surfaces the default level curve fallback (issue #8) — explicit rows if the activity has
    // any, otherwise the generated curve up to upToLevel. Not persisted either way.
    @GetMapping("/activity/{activityId}")
    public ResponseEntity<List<ActivityLevelThresholdDto>> getEffectiveThresholds(
            @PathVariable("activityId") Long activityId,
            @RequestParam(name = "upToLevel", defaultValue = "10") int upToLevel) {
        return ResponseEntity.ok(activityLevelThresholdService.getEffectiveThresholds(activityId, upToLevel));
    }
}
