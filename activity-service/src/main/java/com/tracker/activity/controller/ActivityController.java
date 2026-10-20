package com.tracker.activity.controller;

import com.tracker.activity.dto.ActivityDeleteResponse;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.dto.ActivityUpdateRequest;
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
import java.util.regex.Pattern;

@AllArgsConstructor
@RestController
@RequestMapping("/activity")
@Validated
public class ActivityController {

    private final ActivityService activityService;


    // GET /activity/{id} and GET /activity/{name} share one URL shape, so ONE mapping dispatches on
    // the segment. (Two mappings, /{id:\d+} and /{name}, do not work: Spring does not rank the
    // regex form as more specific and fails every request with "Ambiguous handler methods".)
    // A digits-only segment is an id -- but an activity can legitimately be NAMED with digits
    // ("2048") and name lookups predate ids, so the service tries the id first and falls back to
    // that name. Anything else is a name, exactly as before.
    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");

    // includeInactive is an ADMIN-only switch, enforced at the gateway (SecurityConfig) -- this service
    // has no Spring Security. When false the original no-flag service methods are called unchanged.
    @GetMapping("/{idOrName}")
    public ResponseEntity<ActivityResponseRecord> getActivity(
            @PathVariable @NotBlank(message = "name is required") String idOrName,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        boolean byId = DIGITS_ONLY.matcher(idOrName).matches();
        if (includeInactive) {
            return byId ? activityService.getActivityByIdOrName(idOrName, true)
                    : activityService.getActivity(idOrName, true);
        }
        return byId ? activityService.getActivityByIdOrName(idOrName) : activityService.getActivity(idOrName);
    }

    @GetMapping("/")
    public ResponseEntity<List<ActivityResponseRecord>> getAllActivities(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return includeInactive ? activityService.getAllActivities(true) : activityService.getAllActivities();
    }

    @PostMapping("/")
    public ResponseEntity<ActivityResponseRecord> addActivity(@Valid @RequestBody ActivityRequestRecord activityRequest) {
        return activityService.addActivityEntity(activityRequest);
    }

    // ADMIN only (gateway). Partial update; also the active toggle.
    @PatchMapping("/{id}")
    public ResponseEntity<ActivityResponseRecord> updateActivity(@PathVariable Long id,
                                                                  @Valid @RequestBody ActivityUpdateRequest request) {
        return activityService.updateActivity(id, request);
    }

    // ADMIN only (gateway). Soft delete while history references the activity, hard delete otherwise.
    @DeleteMapping("/{id}")
    public ResponseEntity<ActivityDeleteResponse> deleteActivity(@PathVariable Long id) {
        return activityService.deleteActivity(id);
    }
}
