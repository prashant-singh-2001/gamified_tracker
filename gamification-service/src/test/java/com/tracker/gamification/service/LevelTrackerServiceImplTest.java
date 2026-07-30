package com.tracker.gamification.service;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dao.LevelTrackerArchive;
import com.tracker.gamification.dao.LevelUpEvent;
import com.tracker.gamification.domain.LevelCurve;
import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.repository.LevelTrackerArchiveRepository;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.repository.LevelUpEventRepository;
import com.tracker.gamification.service.impl.LevelTrackerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    @Mock
    private LevelUpEventRepository levelUpEventRepository;

    // Real curve (not @Mock) — @InjectMocks needs an actual computation here, not a stub returning
    // 0/false for everything. Values match the plan's worked examples (base 100, exponent 1.5).
    @Spy
    private LevelCurve levelCurve = new LevelCurve(100.0, 1.5, 100, true);

    @InjectMocks
    private LevelTrackerServiceImpl levelTrackerService;

    @Test
    @DisplayName("save persists a LevelUpEvent when a level threshold is crossed")
    void save_persistsLevelUpEvent_whenThresholdCrossed() {
        // Arrange — same setup as testSaveLevelUp: 300 XP crosses the level-2 threshold (200)
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 300.0);

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
        when(activityLevelThresholdRepository.findReachedLevels(eq(1L), eq(300.0), any(Pageable.class)))
                .thenReturn(List.of(levelThreshold));
        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(leveledUpTracker);

        // Act
        levelTrackerService.save(1L, request);

        // Assert — a level-up event is emitted, carrying the caller, old/new level, XP, and unread state
        verify(levelUpEventRepository).save(argThat((LevelUpEvent e) ->
                e.getUserId().equals(1L)
                        && e.getActivityId().equals(1L)
                        && e.getOldLevel() == null // freshTracker had no level set yet
                        && e.getNewLevel() == 2
                        && e.getTotalXp() == 300.0
                        && e.getCurrentLevelXp() == 100.0
                        && !e.isRead()));
    }

    @Test
    @DisplayName("save does NOT persist a LevelUpEvent when the tracker is still in progress")
    void save_doesNotPersistEvent_whenInProgress() {
        // Arrange — same setup as testSaveNewTracker: no threshold reached, stays level 1
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 100.0);

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
        when(activityLevelThresholdRepository.findReachedLevels(eq(1L), eq(100.0), any(Pageable.class)))
                .thenReturn(List.of());
        // Activity HAS explicit rows (just none reached yet) — explicit-threshold path, default
        // curve must NOT apply here even though findReachedLevels came back empty.
        when(activityLevelThresholdRepository.countForActivity(1L)).thenReturn(3L);
        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(savedTracker);

        // Act
        levelTrackerService.save(1L, request);

        // Assert — no level-up, so no event
        verify(levelUpEventRepository, never()).save(any());
    }

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
        // IDOR fix: userId no longer lives on the DTO — it's a separate trusted argument.
        // LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 100.0);
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 100.0);

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
        // Activity HAS explicit rows — explicit-threshold path, default curve must not apply.
        when(activityLevelThresholdRepository.countForActivity(1L)).thenReturn(3L);

        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(savedTracker);

        // Act
        // LevelTrackerDto result = levelTrackerService.save(request);
        LevelTrackerDto result = levelTrackerService.save(1L, request);

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
        // LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 50.0);
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 50.0);

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
        // LevelTrackerDto result = levelTrackerService.save(request);
        LevelTrackerDto result = levelTrackerService.save(1L, request);

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
        // LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 300.0);
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 300.0);

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
        // LevelTrackerDto result = levelTrackerService.save(request);
        LevelTrackerDto result = levelTrackerService.save(1L, request);

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
        // LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 1L, 0.0);
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 0.0);

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
        // LevelTrackerDto result = levelTrackerService.save(request);
        LevelTrackerDto result = levelTrackerService.save(1L, request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.level());
        assertEquals(0.0, result.totalXp());
        verify(levelTrackerRepository).save(any(LevelTracker.class));
    }

    @Test
    @DisplayName("findById exposes xpForNextLevel and progressPercent from the next threshold")
    void testFindById_includesProgress() {
        // Arrange
        Long id = 1L;
        LevelTracker tracker = LevelTracker.builder()
                .id(id).userId(1L).activityId(1L)
                .level(3).totalXp(640.0).currentLevelXp(140.0)
                .build();

        ActivityLevelThresholdId nextId = ActivityLevelThresholdId.builder().activityId(1L).level(4).build();
        ActivityLevelThreshold next = ActivityLevelThreshold.builder().id(nextId).xpRequired(1000.0).build();

        when(levelTrackerRepository.findById(id)).thenReturn(Optional.of(tracker));
        when(activityLevelThresholdRepository.findNextLevels(eq(1L), eq(640.0), any(Pageable.class)))
                .thenReturn(List.of(next));

        // Act
        LevelTrackerDto result = levelTrackerService.findById(id);

        // Assert
        assertEquals(360.0, result.xpForNextLevel());
        assertEquals(28.0, result.progressPercent());
    }

    @Test
    @DisplayName("findById reports max-level progress when no threshold is ahead")
    void testFindById_maxLevel() {
        // Arrange
        Long id = 1L;
        LevelTracker tracker = LevelTracker.builder()
                .id(id).userId(1L).activityId(1L)
                .level(10).totalXp(50000.0).currentLevelXp(1000.0)
                .build();

        when(levelTrackerRepository.findById(id)).thenReturn(Optional.of(tracker));
        when(activityLevelThresholdRepository.findNextLevels(eq(1L), eq(50000.0), any(Pageable.class)))
                .thenReturn(List.of());

        // Act
        LevelTrackerDto result = levelTrackerService.findById(id);

        // Assert
        assertEquals(0.0, result.xpForNextLevel());
        assertEquals(100.0, result.progressPercent());
    }

    @Test
    @DisplayName("findByUserId batches the threshold lookup instead of querying per tracker")
    void testFindByUserId_batchesThresholdLookup() {
        // Arrange
        Long userId = 1L;
        LevelTracker tracker1 = LevelTracker.builder()
                .id(1L).userId(userId).activityId(1L).level(3).totalXp(640.0).currentLevelXp(140.0).build();
        LevelTracker tracker2 = LevelTracker.builder()
                .id(2L).userId(userId).activityId(2L).level(2).totalXp(200.0).currentLevelXp(100.0).build();

        when(levelTrackerRepository.findAllByUserId(userId)).thenReturn(List.of(tracker1, tracker2));
        when(activityLevelThresholdRepository.findAllForActivities(anyCollection())).thenReturn(List.of());

        // Act
        List<LevelTrackerDto> results = levelTrackerService.findByUserId(userId);

        // Assert — batched lookup called exactly once; the per-row lookup is never used on this path
        assertEquals(2, results.size());
        verify(activityLevelThresholdRepository, times(1)).findAllForActivities(anyCollection());
        verify(activityLevelThresholdRepository, never()).findNextLevels(anyLong(), anyDouble(), any());
    }

    @Test
    @DisplayName("save returns progress toward the next level alongside the persisted tracker")
    void testSave_includesProgress() {
        // Arrange — same setup as testSaveLevelUp: 300 XP crosses the level-2 threshold (200)
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 300.0);

        ActivityLevelThresholdId levelId = ActivityLevelThresholdId.builder().activityId(1L).level(2).build();
        ActivityLevelThreshold levelThreshold = ActivityLevelThreshold.builder().id(levelId).xpRequired(200.0).build();

        ActivityLevelThresholdId nextId = ActivityLevelThresholdId.builder().activityId(1L).level(3).build();
        ActivityLevelThreshold nextThreshold = ActivityLevelThreshold.builder().id(nextId).xpRequired(500.0).build();

        LevelTracker freshTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L).totalXp(0.0).currentLevelXp(0.0).build();
        LevelTracker leveledUpTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L).level(2).totalXp(300.0).currentLevelXp(100.0).build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(1);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L)).thenReturn(Optional.of(freshTracker));
        when(activityLevelThresholdRepository.findReachedLevels(eq(1L), eq(300.0), any(Pageable.class)))
                .thenReturn(List.of(levelThreshold));
        when(levelTrackerRepository.save(any(LevelTracker.class))).thenReturn(leveledUpTracker);
        when(activityLevelThresholdRepository.findNextLevels(eq(1L), eq(300.0), any(Pageable.class)))
                .thenReturn(List.of(nextThreshold));

        // Act
        LevelTrackerDto result = levelTrackerService.save(1L, request);

        // Assert — bandStart = 300 - 100 = 200, span = 500 - 200 = 300, remaining = 200, percent = 33.33
        assertEquals(200.0, result.xpForNextLevel());
        assertEquals(33.33, result.progressPercent());
    }

    @Test
    @DisplayName("default curve resolves a level when the activity has no explicit threshold rows")
    void save_appliesDefaultCurve_whenActivityHasNoThresholdRows() {
        // Arrange — activity 1 has ZERO threshold rows; 300 XP should resolve via the curve
        // (level 3, since xpRequiredFor(3) == 282.84... <= 300 < xpRequiredFor(4) == 519.6...)
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 300.0);

        LevelTracker freshTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L).totalXp(0.0).currentLevelXp(0.0).build();

        LevelTracker curveResolvedTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L)
                .level(3).totalXp(300.0).currentLevelXp(17.15728752538094)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(1);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(freshTracker));
        when(activityLevelThresholdRepository.findReachedLevels(eq(1L), eq(300.0), any(Pageable.class)))
                .thenReturn(List.of());
        when(activityLevelThresholdRepository.countForActivity(1L)).thenReturn(0L);
        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(curveResolvedTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(1L, request);

        // Assert
        assertEquals(3, result.level());
        assertEquals(17.15728752538094, result.currentLevelXp(), 1e-6);
        assertTrue(result.leveledUp()); // previous level defaulted to 1 (fresh tracker) -> 3
        verify(activityLevelThresholdRepository).countForActivity(1L);
    }

    @Test
    @DisplayName("explicit threshold rows win entirely — the curve is never consulted, even below the first row")
    void save_explicitRowsWinOverCurve_whenActivityHasRowsButNoneReached() {
        // Arrange — activity 1 HAS explicit rows (e.g. its first row needs far more than 300 XP),
        // so even though the curve would resolve 300 XP to level 3, this tracker must stay level 1.
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 300.0);

        LevelTracker freshTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L).totalXp(0.0).currentLevelXp(0.0).build();

        LevelTracker inProgressTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L)
                .level(1).totalXp(300.0).currentLevelXp(300.0)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(1);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(freshTracker));
        when(activityLevelThresholdRepository.findReachedLevels(eq(1L), eq(300.0), any(Pageable.class)))
                .thenReturn(List.of());
        when(activityLevelThresholdRepository.countForActivity(1L)).thenReturn(5L);
        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(inProgressTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(1L, request);

        // Assert — level 1, not the curve's level 3; the curve's levelFor/currentLevelXpFor must
        // never even be called once an explicit row exists for this activity.
        assertEquals(1, result.level());
        assertEquals(300.0, result.currentLevelXp());
        assertFalse(result.leveledUp());
        verify(levelCurve, never()).levelFor(anyDouble());
        verify(levelCurve, never()).currentLevelXpFor(anyDouble());
    }

    @Test
    @DisplayName("leveledUp is false when the curve resolves the same level as before")
    void save_leveledUpFalse_whenCurveLevelUnchanged() {
        // Arrange — already level 3 at 300 XP; +5 XP (305 total) is still short of level 4's 519.6
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 5.0);

        LevelTracker existingTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L)
                .level(3).totalXp(300.0).currentLevelXp(17.15728752538094)
                .build();

        LevelTracker updatedTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L)
                .level(3).totalXp(305.0).currentLevelXp(22.15728752538094)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(0);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(existingTracker));
        when(activityLevelThresholdRepository.findReachedLevels(eq(1L), eq(305.0), any(Pageable.class)))
                .thenReturn(List.of());
        when(activityLevelThresholdRepository.countForActivity(1L)).thenReturn(0L);
        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(updatedTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(1L, request);

        // Assert
        assertFalse(result.leveledUp());
        verify(levelUpEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("leveledUp is true only once the curve actually crosses into a new level")
    void save_leveledUpTrue_whenCurveCrossesNextLevel() {
        // Arrange — already level 3 at 300 XP; +250 XP (550 total) crosses level 4's 519.6 boundary
        LevelTrackerRequestDTO request = new LevelTrackerRequestDTO(1L, 250.0);

        LevelTracker existingTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L)
                .level(3).totalXp(300.0).currentLevelXp(17.15728752538094)
                .build();

        LevelTracker leveledUpTracker = LevelTracker.builder()
                .id(1L).userId(1L).activityId(1L)
                .level(4).totalXp(550.0).currentLevelXp(30.3847577293368)
                .build();

        when(levelTrackerRepository.insertIfAbsent(1L, 1L)).thenReturn(0);
        when(levelTrackerRepository.findByUserIdAndActivityIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(existingTracker));
        when(activityLevelThresholdRepository.findReachedLevels(eq(1L), eq(550.0), any(Pageable.class)))
                .thenReturn(List.of());
        when(activityLevelThresholdRepository.countForActivity(1L)).thenReturn(0L);
        when(levelTrackerRepository.save(any(LevelTracker.class)))
                .thenReturn(leveledUpTracker);

        // Act
        LevelTrackerDto result = levelTrackerService.save(1L, request);

        // Assert
        assertEquals(4, result.level());
        assertTrue(result.leveledUp());
        verify(levelUpEventRepository).save(argThat((LevelUpEvent e) ->
                e.getOldLevel() == 3 && e.getNewLevel() == 4));
    }
}





