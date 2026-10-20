package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LogOutcomeDto;
import com.tracker.gamification.service.LogOutcomeService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lives under /activitylog because that is the resource the client is asking about, even though
 * the answer is gamification's: the API gateway routes exactly {@code /api/activitylog/{id}/outcome}
 * here and everything else under /api/activitylog to activity-service (see RouteConfiguration).
 */
@AllArgsConstructor
@RestController
@RequestMapping("/activitylog")
public class LogOutcomeController {

    private final LogOutcomeService logOutcomeService;

    @GetMapping("/{logId}/outcome")
    public ResponseEntity<LogOutcomeDto> getOutcome(@RequestHeader("userId") Long userId,
                                                    @PathVariable Long logId) {
        return ResponseEntity.ok(logOutcomeService.outcomeFor(userId, logId));
    }
}
