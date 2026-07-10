package com.tracker.gamification.service;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dao.LevelTrackerArchive;
import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.repository.LevelTrackerArchiveRepository;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.service.impl.LevelTrackerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Level Tracker Service Tests")
public class LevelTrackerServiceImplTest {

    @Mock
    private LevelTrackerRepository levelTrackerRepository;

    @Mock
    private ActivityLevelThresholdRepository activityLevelThresholdRepository;

    @Mock
    private LevelTrackerArchiveRepository levelTrackerArchiveRepository;

    @InjectMocks
    private LevelTrackerServiceImpl levelTrackerService;

    @Test
    @DisplayName("findByUserId returns mapped list of LevelTrackerDtos")
    void testFindByUserId() {
        // Arrange
        Long userId = 1L;

        LevelTracker tracker1 = LevelTracker.builder()
                .id(1L)
                .userId(userId)
                .activityId(1L)
                .level(3)
                .totalXp(300.0)
                .currentLevelXp(150.0)
                .build();

        LevelTracker tracker2 = LevelTracker.builder()
                .id(2L)
                .userId(userId)
                .activityId(2L)
                .level(2)
                .totalXp(200.0)
                .currentLevelXp(100.0)
                .build();

        when(levelTrackerRepository.findAllByUserId(userId))
                .thenReturn(List.of(tracker1, tracker2));

        // Act
        List<LevelTrackerDto> results = levelTrackerService.findByUserId(userId);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(userId, results.get(0).userId());
        assertEquals(3, results.get(0).level());
        assertEquals(userId, results.get(1).userId());
        assertEquals(2, results.get(1).level());
        verify(levelTrackerRepository).findAllByUserId(userId);
    }

    @Test
    @DisplayName("findByUserId returns empty list when no trackers exist")
    void testFindByUserIdEmpty() {
        // Arrange
        Long userId = 99L;
        when(levelTrackerRepository.findAllByUserId(userId)).thenReturn(List.of());

        // Act
        List<LevelTrackerDto> results = levelTrackerService.findByUserId(userId);

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(levelTrackerRepository).findAllByUserId(userId);
    }

    @Test
    @DisplayName("findByActivityId returns mapped list of LevelTrackerDtos")
    void testFindByActivityId() {
        // Arrange
        Long activityId = 1L;

        LevelTracker tracker1 = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(activityId)
                .level(5)
                .totalXp(500.0)
                .currentLevelXp(250.0)
                .build();

        LevelTracker tracker2 = LevelTracker.builder()
                .id(2L)
                .userId(2L)
                .activityId(activityId)
                .level(3)
                .totalXp(300.0)
                .currentLevelXp(150.0)
                .build();

        when(levelTrackerRepository.findAllByActivityId(activityId))
                .thenReturn(List.of(tracker1, tracker2));

        // Act
        List<LevelTrackerDto> results = levelTrackerService.findByActivityId(activityId);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(activityId, results.get(0).activityId());
        assertEquals(5, results.get(0).level());
        assertEquals(activityId, results.get(1).activityId());
        assertEquals(3, results.get(1).level());
        verify(levelTrackerRepository).findAllByActivityId(activityId);
    }

    @Test
    @DisplayName("findByActivityId returns empty list when no trackers exist")
    void testFindByActivityIdEmpty() {
        // Arrange
        Long activityId = 99L;
        when(levelTrackerRepository.findAllByActivityId(activityId)).thenReturn(List.of());

        // Act
        List<LevelTrackerDto> results = levelTrackerService.findByActivityId(activityId);

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(levelTrackerRepository).findAllByActivityId(activityId);
    }

    @Test
    @DisplayName("findAll returns mapped list of all LevelTrackerDtos")
    void testFindAll() {
        // Arrange
        LevelTracker tracker1 = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .level(2)
                .totalXp(200.0)
                .currentLevelXp(100.0)
                .build();

        LevelTracker tracker2 = LevelTracker.builder()
                .id(2L)
                .userId(2L)
                .activityId(2L)
                .level(4)
                .totalXp(400.0)
                .currentLevelXp(200.0)
                .build();

        when(levelTrackerRepository.findAll()).thenReturn(List.of(tracker1, tracker2));

        // Act
        List<LevelTrackerDto> results = levelTrackerService.findAll();

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        verify(levelTrackerRepository).findAll();
    }

    @Test
    @DisplayName("findById returns mapped LevelTrackerDto")
    void testFindById() {
        // Arrange
        Long id = 1L;
        LevelTracker tracker = LevelTracker.builder()
                .id(id)
                .userId(1L)
                .activityId(1L)
                .level(3)
                .totalXp(300.0)
                .currentLevelXp(150.0)
                .build();

        when(levelTrackerRepository.findById(id)).thenReturn(Optional.of(tracker));

        // Act
        LevelTrackerDto result = levelTrackerService.findById(id);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.userId());
        assertEquals(1L, result.activityId());
        assertEquals(3, result.level());
        assertEquals(300.0, result.totalXp());
        verify(levelTrackerRepository).findById(id);
    }

    @Test
    @DisplayName("findById throws NoSuchElementException when not found")
    void testFindByIdNotFound() {
        // Arrange
        Long id = 99L;
        when(levelTrackerRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> levelTrackerService.findById(id)
        );
        verify(levelTrackerRepository).findById(id);
    }

    @Test
    @DisplayName("save creates new tracker with initial level when no existing tracker")
    void testSaveNewTracker() {
        // Arrange
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 100.0);

        // insertIfAbsent atomically creates the zero-state row when none existed
        LevelTracker freshTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .totalXp(0.0)
                .currentLevelXp(0.0)
                .build();

        LevelTracker savedTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .level(1)
                .totalXp(100.0)
                .currentLevelXp(100.0)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(1);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(freshTracker));

        when(activityLevelThresholdRepository.findReachedLevels(
                eq(1L),
                eq(100.0),
                any(Pageable.class)
        )).thenReturn(List.of());

        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(savedTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.userId());
        assertEquals(1L, result.activityId());
        assertEquals(1, result.level());
        assertEquals(100.0, result.totalXp());
        verify(levelTrackerRepository).insertIfAbsent(1L, 1L);
        verify(levelTrackerRepository).findByUserIdAndActivityIdForUpdate(1L, 1L);
        verify(levelTrackerRepository).save(any(LevelTracker.class));
        // brand-new row: nothing to archive
        verify(levelTrackerArchiveRepository, never()).save(any(LevelTrackerArchive.class));
    }

    @Test
    @DisplayName("save updates existing tracker, archives previous state, and accumulates XP")
    void testSaveExistingTracker() {
        // Arrange
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 50.0);

        LevelTracker existingTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .level(1)
                .totalXp(100.0)
                .currentLevelXp(100.0)
                .build();

        LevelTracker updatedTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .level(1)
                .totalXp(150.0)
                .currentLevelXp(150.0)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(0);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(existingTracker));

        when(activityLevelThresholdRepository.findReachedLevels(
                eq(1L),
                eq(150.0),
                any(Pageable.class)
        )).thenReturn(List.of());

        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(updatedTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.userId());
        assertEquals(150.0, result.totalXp());
        verify(levelTrackerRepository).insertIfAbsent(1L, 1L);
        verify(levelTrackerRepository).findByUserIdAndActivityIdForUpdate(1L, 1L);
        verify(levelTrackerRepository).save(any(LevelTracker.class));
        // pre-existing row: the PREVIOUS state (100.0 XP, before this update) must be archived
        verify(levelTrackerArchiveRepository).save(argThat(archive ->
                archive.getUserId().equals(1L)
                        && archive.getActivityId().equals(1L)
                        && archive.getTotalXp() == 100.0
                        && archive.getCurrentLevelXp() == 100.0
        ));
    }

    @Test
    @DisplayName("save levels up tracker when threshold is reached")
    void testSaveLevelUp() {
        // Arrange
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 300.0);

        ActivityLevelThresholdId levelId = ActivityLevelThresholdId.builder()
                .activityId(1L)
                .level(2)
                .build();

        ActivityLevelThreshold levelThreshold = ActivityLevelThreshold.builder()
                .id(levelId)
                .xpRequired(200.0)
                .build();

        LevelTracker freshTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .totalXp(0.0)
                .currentLevelXp(0.0)
                .build();

        LevelTracker leveledUpTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .level(2)
                .totalXp(300.0)
                .currentLevelXp(100.0)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(1);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(freshTracker));

        when(activityLevelThresholdRepository.findReachedLevels(
                eq(1L),
                eq(300.0),
                any(Pageable.class)
        )).thenReturn(List.of(levelThreshold));

        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(leveledUpTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(request);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.level());
        assertEquals(100.0, result.currentLevelXp());
        assertEquals(300.0, result.totalXp());
        verify(levelTrackerRepository).insertIfAbsent(1L, 1L);
        verify(levelTrackerRepository).findByUserIdAndActivityIdForUpdate(1L, 1L);
        verify(activityLevelThresholdRepository).findReachedLevels(
                eq(1L),
                eq(300.0),
                any(Pageable.class)
        );
        verify(levelTrackerRepository).save(any(LevelTracker.class));
    }

    @Test
    @DisplayName("save with zero XP creates tracker at level 1")
    void testSaveZeroXp() {
        // Arrange
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 0.0);

        LevelTracker freshTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .totalXp(0.0)
                .currentLevelXp(0.0)
                .build();

        LevelTracker savedTracker = LevelTracker.builder()
                .id(1L)
                .userId(1L)
                .activityId(1L)
                .level(1)
                .totalXp(0.0)
                .currentLevelXp(0.0)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(1);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(freshTracker));

        when(activityLevelThresholdRepository.findReachedLevels(
                eq(1L),
                eq(0.0),
                any(Pageable.class)
        )).thenReturn(List.of());

        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(savedTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.level());
        assertEquals(0.0, result.totalXp());
        verify(levelTrackerRepository).save(any(LevelTracker.class));
    }
}





