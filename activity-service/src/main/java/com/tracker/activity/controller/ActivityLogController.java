package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.NaturalLogDraftResponse;
import com.tracker.activity.dto.NaturalLogRequest;
import com.tracker.activity.dto.StreakResponse;
import com.tracker.activity.service.ActivityLogService;
import com.tracker.activity.service.NaturalLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/activitylog")
public class ActivityLogController {

    private final ActivityLogService activityLogService;
    private final NaturalLogService naturalLogService;


    @GetMapping("/{id}")
    public ResponseEntity<ActivityLogResponse> getActivityLog(@PathVariable("id") Long id) {
        return activityLogService.getActivityLogResponseEntity(id);
    }

    @PostMapping("/")
    public ResponseEntity<ActivityLogResponse> addActivityLog(@RequestHeader("userId") Long userId, @Valid @RequestBody ActivityLogRequest activityLogRequest) {
        return activityLogService.addActivityLogResponseResponseEntity(userId, activityLogRequest);
    }

    // Issue #70. Writes nothing -- POST the returned draft to addActivityLog above to actually log
    // it. userId is taken from the trusted header (never a path variable) for the same reason every
    // other endpoint here does, even though this call has no per-user data to look up today.
    @PostMapping("/natural")
    public ResponseEntity<NaturalLogDraftResponse> parseNaturalLog(
            @RequestHeader("userId") Long userId, @Valid @RequestBody NaturalLogRequest request) {
        return naturalLogService.parseNaturalLog(userId, request.text());
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@PathVariable("id") Long id) {
        return activityLogService.getAllActivityForUser(id);
    }

    @GetMapping("/streaks/user/{id}")
    public ResponseEntity<List<StreakResponse>> getAllStreaksForUser(@PathVariable("id") Long id) {
        return activityLogService.getStreaksForUser(id);
    }


}
