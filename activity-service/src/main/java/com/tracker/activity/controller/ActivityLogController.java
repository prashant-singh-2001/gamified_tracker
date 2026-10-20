package com.tracker.activity.controller;

import com.tracker.activity.domain.ActivityLogQuery;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.NaturalLogDraftResponse;
import com.tracker.activity.dto.NaturalLogRequest;
import com.tracker.activity.dto.PagedResponse;
import com.tracker.activity.dto.RecentActivityResponse;
import com.tracker.activity.dto.StreakResponse;
import com.tracker.activity.service.ActivityLogService;
import com.tracker.activity.service.NaturalLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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


    // #78/#88: id is an ActivityLog PK, not a userId -- ownership is enforced against the
    // trusted header inside the service (see ActivityLogServiceImpl.getActivityLogResponseEntity).
    @GetMapping("/{id}")
    public ResponseEntity<ActivityLogResponse> getActivityLog(@RequestHeader("userId") Long callerUserId,
                                                               @PathVariable("id") Long id) {
        return activityLogService.getActivityLogResponseEntity(callerUserId, id);
    }

    @PostMapping("/")
    public ResponseEntity<ActivityLogResponse> addActivityLog(@RequestHeader("userId") Long userId, @Valid @RequestBody ActivityLogRequest activityLogRequest) {
        return activityLogService.addActivityLogResponseResponseEntity(userId, activityLogRequest);
    }

    // Issue #70. Writes nothing -- POST the returned draft to addActivityLog above to actually log
    // it. userId is taken from the trusted header (never a path variable) for the same reason every
    // other endpoint here does, even though this call has no per-user data to look up today.
    @Operation(summary = "Preview a log from one free-text sentence (writes nothing)",
            description = "Parses text such as \"studied Spring Boot for 90 minutes this morning\" into a draft request "
                    + "body for POST /activitylog/. Never logs anything; the client posts the draft to commit it. "
                    + "The HTTP status is always 200 and `status` tells the outcome. **`DISABLED` is a stable part of "
                    + "this contract**: the feature needs a model backend and is off by default "
                    + "(natural-log.enabled=false), so a client must treat `DISABLED` as a normal state, hide or "
                    + "disable the natural-language input, and not show an error.")
    @ApiResponse(responseCode = "200", description = "Always 200. See `status`: PARSED, NEEDS_CLARIFICATION, "
            + "DISABLED (feature switched off) or UNAVAILABLE (feature on, model did not answer).",
            content = @Content(mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NaturalLogDraftResponse.class),
                    examples = {
                    @ExampleObject(name = "PARSED", value = "{\"draft\":{\"activityName\":\"Study\","
                            + "\"startTime\":\"2026-10-20T08:00:00\",\"endTime\":\"2026-10-20T09:30:00\","
                            + "\"notes\":null},\"interpretation\":\"Study for 90 minutes (2026-10-20T08:00 to "
                            + "2026-10-20T09:30)\",\"status\":\"PARSED\",\"nameResolution\":null,\"suggestions\":[]}"),
                    @ExampleObject(name = "NEEDS_CLARIFICATION", value = "{\"draft\":null,\"interpretation\":"
                            + "\"Couldn't tell how long this took -- try including a duration.\","
                            + "\"status\":\"NEEDS_CLARIFICATION\",\"nameResolution\":null,\"suggestions\":[]}"),
                    @ExampleObject(name = "DISABLED", summary = "Feature off (the default). Stable contract.",
                            value = "{\"draft\":null,\"interpretation\":null,\"status\":\"DISABLED\","
                                    + "\"nameResolution\":null,\"suggestions\":[]}"),
                    @ExampleObject(name = "UNAVAILABLE", value = "{\"draft\":null,\"interpretation\":null,"
                            + "\"status\":\"UNAVAILABLE\",\"nameResolution\":null,\"suggestions\":[]}")
            }))
    @PostMapping("/natural")
    public ResponseEntity<NaturalLogDraftResponse> parseNaturalLog(
            @RequestHeader("userId") Long userId, @Valid @RequestBody NaturalLogRequest request) {
        return naturalLogService.parseNaturalLog(userId, request.text());
    }

    // #79/#88: ownership enforced against the trusted header inside the service.
    //
    // The UNPAGED read: a bare array of every log, exactly as it has always been. It only matches
    // when none of the new query parameters is present, so "unpaged keeps working" is guaranteed by
    // routing rather than by an if-statement -- passing any of them selects the paged handler below.
    @GetMapping(value = "/user/{id}", params = {"!page", "!size", "!sort", "!activityId", "!from", "!to"})
    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@RequestHeader("userId") Long callerUserId,
                                                                            @PathVariable("id") Long id) {
        return activityLogService.getAllActivityForUser(callerUserId, id);
    }

    // The PAGED read, selected by the presence of any of page, size, sort, activityId, from, to.
    // Returns an envelope {content, page, size, totalElements, totalPages, sort}, not a bare array.
    // Raw strings, so a bad value is a 400 ProblemDetail naming the parameter (see ActivityLogQuery).
    @GetMapping("/user/{id}")
    public ResponseEntity<PagedResponse<ActivityLogResponse>> getActivityForUserPaged(
            @RequestHeader("userId") Long callerUserId,
            @PathVariable("id") Long id,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "activityId", required = false) String activityId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        return activityLogService.getActivityForUserPaged(callerUserId, id,
                ActivityLogQuery.parse(page, size, sort, activityId, from, to));
    }

    // Quick picks for the "log an activity" screen: the caller's most-logged ACTIVE activities, most
    // logged first, each with the last time it was used. ?limit= is 1..20 (default 5); a bad value is
    // a 400. Owner-only, like every per-user read -- ownership is enforced inside the service.
    @GetMapping("/user/{id}/recent")
    public ResponseEntity<List<RecentActivityResponse>> getRecentActivities(
            @RequestHeader("userId") Long callerUserId,
            @PathVariable("id") Long id,
            @RequestParam(name = "limit", required = false) String limit) {
        return activityLogService.getRecentActivities(callerUserId, id, ActivityLogQuery.parseRecentLimit(limit));
    }

    // #80/#88: ownership enforced against the trusted header inside the service.
    @GetMapping("/streaks/user/{id}")
    public ResponseEntity<List<StreakResponse>> getAllStreaksForUser(@RequestHeader("userId") Long callerUserId,
                                                                      @PathVariable("id") Long id) {
        return activityLogService.getStreaksForUser(callerUserId, id);
    }


}
