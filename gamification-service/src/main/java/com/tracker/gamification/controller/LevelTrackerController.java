package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.dto.ManualXpAwardRequest;
import com.tracker.gamification.service.impl.LevelTrackerServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/level")
@Validated
public class LevelTrackerController {

    @Autowired
    private LevelTrackerServiceImpl levelTrackerService;

    // #88: every user's tracker rows, no per-user subject to compare -- ADMIN-gated at the
    // gateway (SecurityConfig) rather than an ownership check here.
    @GetMapping
    public ResponseEntity<List<LevelTrackerDto>> getAllLevelTracker() {
        return ResponseEntity.ok(levelTrackerService.findAll());
    }

    // #77/#88: id is a LevelTracker PK, not a userId -- ownership is enforced against the
    // trusted header inside the service (see LevelTrackerServiceImpl.findById).
    @GetMapping("/{id}")
    public ResponseEntity<LevelTrackerDto> getLevelTrackerById(@RequestHeader("userId") Long callerUserId,
                                                                @PathVariable @Positive(message = "id cannot be negative or zero") Long id) {
        return ResponseEntity.ok(levelTrackerService.findById(callerUserId, id));
    }

    // IDOR fix: userId now comes from the trusted "userId" header (injected by the
    // gateway, forwarded by activity-service's internal Feign call), not from the body.
    // @PostMapping
    // public ResponseEntity<LevelTrackerDto> createLevelTracker(@RequestBody LevelTrackerRequestDTO levelTrackerRequestDTO) {
    //     return ResponseEntity.ok(levelTrackerService.save(levelTrackerRequestDTO));
    // }
    // #74: this was a public, unbounded XP mint — any authenticated user could award
    // themselves arbitrary XP for arbitrary activityId, bypassing activity-service, the
    // outbox, and the idempotency guard entirely. Gated hasRole("ADMIN") at the gateway
    // (SecurityConfig) and replaced with a capped, audited manual-award door.
    // @PostMapping
    // public ResponseEntity<LevelTrackerDto> createLevelTracker(@RequestHeader("userId") @Positive(message = "id cannot be negative or zero") Long userId,
    //                                                            @Valid @RequestBody LevelTrackerRequestDTO levelTrackerRequestDTO) {
    //     return ResponseEntity.ok(levelTrackerService.save(userId, levelTrackerRequestDTO));
    // }
    @PostMapping
    public ResponseEntity<LevelTrackerDto> awardXpManually(@RequestHeader("userId") @Positive(message = "id cannot be negative or zero") Long actorUserId,
                                                             @Valid @RequestBody ManualXpAwardRequest request) {
        return ResponseEntity.ok(levelTrackerService.awardManually(actorUserId, request));
    }

    // #76/#88: ownership enforced against the trusted header inside the service.
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LevelTrackerDto>> getLevelTrackerByUserId(@RequestHeader("userId") Long callerUserId,
                                                                          @PathVariable @Positive(message = "id cannot be negative or zero") Long userId) {
        return ResponseEntity.ok(levelTrackerService.findByUserId(callerUserId, userId));
    }

    // #88: returns every user's tracker row for this activity -- there is no single subject to
    // compare against a caller, so this is ADMIN-gated at the gateway (SecurityConfig) instead
    // of an ownership check here. GET /leaderboard/activity/{id} is the public equivalent.
    @GetMapping("/activity/{activityId}")
    public ResponseEntity<List<LevelTrackerDto>> getLevelTrackerByActivityId(@PathVariable @Positive(message = "id cannot be negative or zero") Long activityId) {
        return ResponseEntity.ok(levelTrackerService.findByActivityId(activityId));
    }
}