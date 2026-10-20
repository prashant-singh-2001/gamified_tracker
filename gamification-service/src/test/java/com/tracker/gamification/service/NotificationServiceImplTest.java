package com.tracker.gamification.service;

import com.tracker.gamification.dao.LevelUpEvent;
import com.tracker.gamification.dto.LevelUpEventDto;
import com.tracker.gamification.repository.LevelUpEventRepository;
import com.tracker.gamification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Service Tests")
class NotificationServiceImplTest {

    @Mock
    private LevelUpEventRepository levelUpEventRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    @DisplayName("getForUser(unreadOnly=false) uses findByUserIdOrderByCreatedAtDesc and maps to DTOs")
    void getForUser_all_mapsToDto() {
        LevelUpEvent event = LevelUpEvent.builder()
                .id(10L)
                .userId(1L)
                .activityId(2L)
                .oldLevel(1)
                .newLevel(2)
                .totalXp(300.0)
                .currentLevelXp(100.0)
                .read(true)
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();

        when(levelUpEventRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(event));

        List<LevelUpEventDto> result = notificationService.getForUser(1L, false);

        assertEquals(1, result.size());
        LevelUpEventDto dto = result.get(0);
        assertEquals(10L, dto.id());
        assertEquals(2L, dto.activityId());
        assertEquals(1, dto.oldLevel());
        assertEquals(2, dto.newLevel());
        assertEquals(300.0, dto.totalXp());
        assertEquals(100.0, dto.currentLevelXp());
        assertTrue(dto.read());
        assertEquals(LocalDateTime.of(2026, 1, 1, 12, 0), dto.createdAt());

        verify(levelUpEventRepository).findByUserIdOrderByCreatedAtDesc(1L);
        verify(levelUpEventRepository, never()).findByUserIdAndReadFalseOrderByCreatedAtDesc(eq(1L));
    }

    @Test
    @DisplayName("getForUser(unreadOnly=true) uses findByUserIdAndReadFalseOrderByCreatedAtDesc")
    void getForUser_unreadOnly_usesUnreadQuery() {
        when(levelUpEventRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        List<LevelUpEventDto> result = notificationService.getForUser(1L, true);

        assertTrue(result.isEmpty());
        verify(levelUpEventRepository).findByUserIdAndReadFalseOrderByCreatedAtDesc(1L);
        verify(levelUpEventRepository, never()).findByUserIdOrderByCreatedAtDesc(eq(1L));
    }

    @Test
    @DisplayName("unreadCount delegates to countByUserIdAndReadFalse")
    void unreadCount_delegatesToRepository() {
        when(levelUpEventRepository.countByUserIdAndReadFalse(1L)).thenReturn(3L);

        assertEquals(3L, notificationService.unreadCount(1L));
        verify(levelUpEventRepository).countByUserIdAndReadFalse(1L);
    }

    @Test
    @DisplayName("markAllRead delegates to the bulk update and returns how many rows it flipped")
    void markAllRead_delegatesAndReturnsTheCount() {
        when(levelUpEventRepository.markAllReadForUser(1L)).thenReturn(4);

        assertEquals(4, notificationService.markAllRead(1L));
        verify(levelUpEventRepository).markAllReadForUser(1L);
    }

    @Test
    @DisplayName("markRead sets read=true and saves when the event is owned by the caller")
    void markRead_found_marksReadAndSaves() {
        LevelUpEvent event = LevelUpEvent.builder()
                .id(5L)
                .userId(1L)
                .activityId(2L)
                .newLevel(2)
                .read(false)
                .build();

        when(levelUpEventRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(event));

        notificationService.markRead(1L, 5L);

        assertTrue(event.isRead());
        verify(levelUpEventRepository).save(event);
    }

    @Test
    @DisplayName("markRead throws NoSuchElementException when the event is missing or not owned by the caller")
    void markRead_notFoundOrNotOwned_throws() {
        when(levelUpEventRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> notificationService.markRead(1L, 99L));
        verify(levelUpEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
