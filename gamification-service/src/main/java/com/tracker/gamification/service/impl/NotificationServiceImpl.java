package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.LevelUpEvent;
import com.tracker.gamification.dto.LevelUpEventDto;
import com.tracker.gamification.repository.LevelUpEventRepository;
import com.tracker.gamification.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;


@AllArgsConstructor
@Service
public class NotificationServiceImpl implements NotificationService {

    private final LevelUpEventRepository levelUpEventRepository;

    @Override
    public List<LevelUpEventDto> getForUser(Long userId, boolean unreadOnly) {
        var events = unreadOnly ?
                levelUpEventRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                : levelUpEventRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return events.stream().map(this::toDto).toList();
    }

    @Override
    public long unreadCount(Long userId) {
        return levelUpEventRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long eventId) {
        var event = levelUpEventRepository.findByIdAndUserId(eventId, userId).orElseThrow(() -> new NoSuchElementException("Notification " + eventId + " not found"));
        event.setRead(true);
        levelUpEventRepository.save(event);
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        return levelUpEventRepository.markAllReadForUser(userId);
    }

    private LevelUpEventDto toDto(LevelUpEvent e) {
        return new LevelUpEventDto(e.getId(), e.getActivityId(), e.getOldLevel(),
                e.getNewLevel(), e.getTotalXp(), e.getCurrentLevelXp(), e.isRead(), e.getCreatedAt());
    }
}
