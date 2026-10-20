package com.tracker.gamification.service;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import com.tracker.gamification.domain.LevelCurve;
import com.tracker.gamification.dto.ActivityLevelThresholdDto;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.service.impl.ActivityLevelThresholdServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Level Threshold Service Tests")
public class ActivityLevelThresholdServiceImplTest {

    @Mock
    private ActivityLevelThresholdRepository activityLevelThresholdRepository;

    @Spy
    private LevelCurve levelCurve = new LevelCurve(100.0, 1.5, 100, true);

    @InjectMocks
    private ActivityLevelThresholdServiceImpl activityLevelThresholdService;

    // ---- item 12: PUT / DELETE and the monotonic-curve guard --------------------------------------

    private static ActivityLevelThreshold row(long activityId, int level, double xp) {
        return ActivityLevelThreshold.builder()
                .id(new ActivityLevelThresholdId(activityId, level)).xpRequired(xp).build();
    }

    private void curve(long activityId, double... xpByLevel) {
        var rows = new java.util.ArrayList<ActivityLevelThreshold>();
        for (int i = 0; i < xpByLevel.length; i++) {
            rows.add(row(activityId, i + 1, xpByLevel[i]));
        }
        when(activityLevelThresholdRepository.findAllForActivity(activityId)).thenReturn(rows);
    }

    @Test
    @DisplayName("update changes the row's XP when it stays between its neighbours")
    void testUpdateBetweenNeighbours() {
        curve(1L, 100, 250, 500);
        when(activityLevelThresholdRepository.findById(new ActivityLevelThresholdId(1L, 2)))
                .thenReturn(Optional.of(row(1L, 2, 250)));
        when(activityLevelThresholdRepository.save(any(ActivityLevelThreshold.class))).thenAnswer(i -> i.getArgument(0));

        var result = activityLevelThresholdService.updateActivityLevelThreshold(1L, 2, 300.0);

        assertEquals(300.0, result.xpRequired());
        assertEquals(2, result.level());
    }

    @Test
    @DisplayName("update rejects XP at or below the previous level, or at or above the next")
    void testUpdateRejectsNonMonotonic() {
        curve(1L, 100, 250, 500);
        when(activityLevelThresholdRepository.findById(new ActivityLevelThresholdId(1L, 2)))
                .thenReturn(Optional.of(row(1L, 2, 250)));

        var low = assertThrows(com.tracker.gamification.exception.InvalidThresholdCurveException.class,
                () -> activityLevelThresholdService.updateActivityLevelThreshold(1L, 2, 100.0));
        assertTrue(low.getMessage().contains("more XP than level 1"));
        assertThrows(com.tracker.gamification.exception.InvalidThresholdCurveException.class,
                () -> activityLevelThresholdService.updateActivityLevelThreshold(1L, 2, 500.0));
        assertThrows(com.tracker.gamification.exception.InvalidThresholdCurveException.class,
                () -> activityLevelThresholdService.updateActivityLevelThreshold(1L, 2, 900.0));
        verify(activityLevelThresholdRepository, never()).save(any(ActivityLevelThreshold.class));
    }

    @Test
    @DisplayName("update of the first and last level only has one neighbour to respect")
    void testUpdateEdgeLevels() {
        curve(1L, 100, 250, 500);
        when(activityLevelThresholdRepository.findById(new ActivityLevelThresholdId(1L, 1)))
                .thenReturn(Optional.of(row(1L, 1, 100)));
        when(activityLevelThresholdRepository.findById(new ActivityLevelThresholdId(1L, 3)))
                .thenReturn(Optional.of(row(1L, 3, 500)));
        when(activityLevelThresholdRepository.save(any(ActivityLevelThreshold.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1.0, activityLevelThresholdService.updateActivityLevelThreshold(1L, 1, 1.0).xpRequired());
        assertEquals(10_000.0, activityLevelThresholdService.updateActivityLevelThreshold(1L, 3, 10_000.0).xpRequired());
    }

    @Test
    @DisplayName("update of a level that does not exist is a not-found, not an upsert")
    void testUpdateMissing() {
        when(activityLevelThresholdRepository.findById(any(ActivityLevelThresholdId.class))).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> activityLevelThresholdService.updateActivityLevelThreshold(1L, 7, 100.0));
        verify(activityLevelThresholdRepository, never()).save(any(ActivityLevelThreshold.class));
    }

    @Test
    @DisplayName("POST (upsert) is held to the same curve rule, so the PUT guard cannot be bypassed")
    void testSaveRejectsNonMonotonic() {
        curve(1L, 100, 250, 500);

        assertThrows(com.tracker.gamification.exception.InvalidThresholdCurveException.class,
                () -> activityLevelThresholdService.saveActivityLevelThreshold(new ActivityLevelThresholdDto(1L, 2, 90.0)));
        verify(activityLevelThresholdRepository, never()).save(any(ActivityLevelThreshold.class));
    }

    @Test
    @DisplayName("POST of a brand-new level between its neighbours is fine")
    void testSaveNewLevelBetweenNeighbours() {
        when(activityLevelThresholdRepository.findAllForActivity(1L))
                .thenReturn(List.of(row(1L, 1, 100), row(1L, 3, 500)));

        var result = activityLevelThresholdService.saveActivityLevelThreshold(new ActivityLevelThresholdDto(1L, 2, 300.0));

        assertEquals(300.0, result.xpRequired());
    }

    @Test
    @DisplayName("delete removes an existing row; a missing one is a not-found")
    void testDelete() {
        var id = new ActivityLevelThresholdId(1L, 2);
        when(activityLevelThresholdRepository.existsById(id)).thenReturn(true);

        activityLevelThresholdService.deleteActivityLevelThreshold(1L, 2);

        verify(activityLevelThresholdRepository).deleteById(id);

        when(activityLevelThresholdRepository.existsById(new ActivityLevelThresholdId(1L, 9))).thenReturn(false);
        assertThrows(java.util.NoSuchElementException.class,
                () -> activityLevelThresholdService.deleteActivityLevelThreshold(1L, 9));
        verify(activityLevelThresholdRepository, never()).deleteById(new ActivityLevelThresholdId(1L, 9));
    }

    @Test
    @DisplayName("getActivityLevelThresholdById returns mapped ActivityLevelThresholdDto")
    void testGetActivityLevelThresholdById() {
        // Arrange
        ActivityLevelThresholdId id = ActivityLevelThresholdId.builder()
                .activityId(1L)
                .level(1)
                .build();

        ActivityLevelThreshold threshold = ActivityLevelThreshold.builder()
                .id(id)
                .xpRequired(100.0)
                .build();

        ActivityLevelThresholdDto requestDto = new ActivityLevelThresholdDto(1L, 1, 100.0);

        when(activityLevelThresholdRepository.findById(any(ActivityLevelThresholdId.class)))
                .thenReturn(Optional.of(threshold));

        // Act
        ActivityLevelThresholdDto result = activityLevelThresholdService.getActivityLevelThresholdById(requestDto);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.activityId());
        assertEquals(1, result.level());
        assertEquals(100.0, result.xpRequired());
        verify(activityLevelThresholdRepository).findById(any(ActivityLevelThresholdId.class));
    }

    @Test
    @DisplayName("getActivityLevelThresholdById throws NoSuchElementException when not found")
    void testGetActivityLevelThresholdByIdNotFound() {
        // Arrange
        ActivityLevelThresholdDto requestDto = new ActivityLevelThresholdDto(99L, 99, 500.0);

        when(activityLevelThresholdRepository.findById(any(ActivityLevelThresholdId.class)))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> activityLevelThresholdService.getActivityLevelThresholdById(requestDto),
                "ActivityLevelThreshold not found"
        );
        verify(activityLevelThresholdRepository).findById(any(ActivityLevelThresholdId.class));
    }

    @Test
    @DisplayName("saveActivityLevelThreshold saves and returns mapped dto")
    void testSaveActivityLevelThreshold() {
        // Arrange
        ActivityLevelThresholdDto requestDto = new ActivityLevelThresholdDto(2L, 2, 250.0);

        ActivityLevelThresholdId id = ActivityLevelThresholdId.builder()
                .activityId(2L)
                .level(2)
                .build();

        ActivityLevelThreshold entity = ActivityLevelThreshold.builder()
                .id(id)
                .xpRequired(250.0)
                .build();

        when(activityLevelThresholdRepository.save(any(ActivityLevelThreshold.class)))
                .thenReturn(entity);

        // Act
        ActivityLevelThresholdDto result = activityLevelThresholdService.saveActivityLevelThreshold(requestDto);

        // Assert
        assertNotNull(result);
        assertEquals(2L, result.activityId());
        assertEquals(2, result.level());
        assertEquals(250.0, result.xpRequired());
        verify(activityLevelThresholdRepository).save(any(ActivityLevelThreshold.class));
    }

    @Test
    @DisplayName("saveActivityLevelThreshold with zero xp")
    void testSaveActivityLevelThresholdZeroXp() {
        // Arrange
        ActivityLevelThresholdDto requestDto = new ActivityLevelThresholdDto(1L, 1, 0.0);

        ActivityLevelThresholdId id = ActivityLevelThresholdId.builder()
                .activityId(1L)
                .level(1)
                .build();

        ActivityLevelThreshold entity = ActivityLevelThreshold.builder()
                .id(id)
                .xpRequired(0.0)
                .build();

        when(activityLevelThresholdRepository.save(any(ActivityLevelThreshold.class)))
                .thenReturn(entity);

        // Act
        ActivityLevelThresholdDto result = activityLevelThresholdService.saveActivityLevelThreshold(requestDto);

        // Assert
        assertNotNull(result);
        assertEquals(0.0, result.xpRequired());
        verify(activityLevelThresholdRepository).save(any(ActivityLevelThreshold.class));
    }

    @Test
    @DisplayName("getAllActivityLevelThreshold returns mapped list")
    void testGetAllActivityLevelThreshold() {
        // Arrange
        ActivityLevelThresholdId id1 = ActivityLevelThresholdId.builder()
                .activityId(1L)
                .level(1)
                .build();

        ActivityLevelThresholdId id2 = ActivityLevelThresholdId.builder()
                .activityId(1L)
                .level(2)
                .build();

        ActivityLevelThresholdId id3 = ActivityLevelThresholdId.builder()
                .activityId(2L)
                .level(1)
                .build();

        ActivityLevelThreshold threshold1 = ActivityLevelThreshold.builder()
                .id(id1)
                .xpRequired(100.0)
                .build();

        ActivityLevelThreshold threshold2 = ActivityLevelThreshold.builder()
                .id(id2)
                .xpRequired(250.0)
                .build();

        ActivityLevelThreshold threshold3 = ActivityLevelThreshold.builder()
                .id(id3)
                .xpRequired(150.0)
                .build();

        when(activityLevelThresholdRepository.findAll())
                .thenReturn(List.of(threshold1, threshold2, threshold3));

        // Act
        List<ActivityLevelThresholdDto> results = activityLevelThresholdService.getAllActivityLevelThreshold();

        // Assert
        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals(100.0, results.get(0).xpRequired());
        assertEquals(250.0, results.get(1).xpRequired());
        assertEquals(150.0, results.get(2).xpRequired());
        verify(activityLevelThresholdRepository).findAll();
    }

    @Test
    @DisplayName("getAllActivityLevelThreshold returns empty list")
    void testGetAllActivityLevelThresholdEmpty() {
        // Arrange
        when(activityLevelThresholdRepository.findAll()).thenReturn(List.of());

        // Act
        List<ActivityLevelThresholdDto> results = activityLevelThresholdService.getAllActivityLevelThreshold();

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(activityLevelThresholdRepository).findAll();
    }

    @Test
    @DisplayName("getEffectiveThresholds returns explicit rows when the activity has any")
    void testGetEffectiveThresholds_returnsExplicitRowsWhenPresent() {
        // Arrange
        ActivityLevelThresholdId id = ActivityLevelThresholdId.builder().activityId(1L).level(2).build();
        ActivityLevelThreshold threshold = ActivityLevelThreshold.builder().id(id).xpRequired(50.0).build();

        when(activityLevelThresholdRepository.findAllForActivity(1L)).thenReturn(List.of(threshold));

        // Act
        List<ActivityLevelThresholdDto> results = activityLevelThresholdService.getEffectiveThresholds(1L, 10);

        // Assert — the one explicit row, not the generated curve
        assertEquals(1, results.size());
        assertEquals(2, results.get(0).level());
        assertEquals(50.0, results.get(0).xpRequired());
        verify(activityLevelThresholdRepository).findAllForActivity(1L);
    }

    @Test
    @DisplayName("getEffectiveThresholds returns the generated default curve when the activity has no rows")
    void testGetEffectiveThresholds_returnsGeneratedCurveWhenAbsent() {
        // Arrange
        when(activityLevelThresholdRepository.findAllForActivity(2L)).thenReturn(List.of());

        // Act
        List<ActivityLevelThresholdDto> results = activityLevelThresholdService.getEffectiveThresholds(2L, 4);

        // Assert — levels 1..4, curve-generated, not persisted
        assertEquals(4, results.size());
        assertEquals(1, results.get(0).level());
        assertEquals(0.0, results.get(0).xpRequired());
        assertEquals(2, results.get(1).level());
        assertEquals(100.0, results.get(1).xpRequired());
        assertEquals(4, results.get(3).level());
        assertEquals(519.6152422706632, results.get(3).xpRequired(), 1e-6);
        results.forEach(dto -> assertEquals(2L, dto.activityId()));
    }
}


