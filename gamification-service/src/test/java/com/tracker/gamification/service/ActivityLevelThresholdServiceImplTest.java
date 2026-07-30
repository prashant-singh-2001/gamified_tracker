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


