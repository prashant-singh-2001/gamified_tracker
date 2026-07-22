package com.tracker.gamification.service;

import com.tracker.gamification.repository.OverallLevelThresholdRepository;
import com.tracker.gamification.service.impl.OverallLevelServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Overall Level Service Tests")
public class OverallLevelServiceImplTest {

    @Mock
    private OverallLevelThresholdRepository overallLevelThresholdRepository;

    @InjectMocks
    private OverallLevelServiceImpl overallLevelService;

    @Test
    @DisplayName("returns the repository's reached level when present")
    void overallLevelFor_returnsReachedLevel() {
        when(overallLevelThresholdRepository.findReachedLevel(eq(1200.0), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(4));

        assertEquals(4, overallLevelService.overallLevelFor(1200.0));
    }

    @Test
    @DisplayName("defaults to level 1 when no threshold is reached yet")
    void overallLevelFor_defaultsToOne_whenNoThresholdReached() {
        when(overallLevelThresholdRepository.findReachedLevel(eq(0.0), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of());

        assertEquals(1, overallLevelService.overallLevelFor(0.0));
    }
}
