package com.tracker.gamification.controller;

import com.tracker.gamification.dto.UserAchievementDto;
import com.tracker.gamification.service.AchievementService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Caller-scoped badge feed. Like NotificationController, the user is taken from the trusted
 * "userId" header the gateway injects — never from a path variable or the body — so one user
 * cannot read another's trophy case.
 */
@AllArgsConstructor
@RestController
@RequestMapping("/achievements")
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping
    public ResponseEntity<List<UserAchievementDto>> findUnlocked(@RequestHeader("userId") Long userId) {
        return ResponseEntity.ok(achievementService.findUnlocked(userId));
    }
}
