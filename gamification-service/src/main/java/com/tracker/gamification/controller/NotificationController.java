package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LevelUpEventDto;
import com.tracker.gamification.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<LevelUpEventDto>> findAllByUserId(@RequestHeader Long userId, @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(notificationService.getForUser(userId, unreadOnly));
    }

    // Both keys carry the same number. "Count" (capital C) is the original key and is DEPRECATED: it
    // was a naming slip, every other field in the API is camelCase. It stays so existing clients keep
    // working; new code reads "count". Removing "Count" is a breaking change to schedule with the
    // frontend, not to slip in.
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> findAllByUserId(@RequestHeader Long userId) {
        long unread = notificationService.unreadCount(userId);
        return ResponseEntity.ok(Map.of("count", unread, "Count", unread));
    }

    // Marks every unread notification of the caller read. Idempotent: calling it again flips nothing
    // and answers {"updated": 0}. Only ever touches the caller's own rows (userId from the trusted header).
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@RequestHeader("userId") Long userId) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@RequestHeader("userId") Long userId, @PathVariable Long id) {
        notificationService.markRead(userId, id);
        return ResponseEntity.noContent().build();
    }
}
