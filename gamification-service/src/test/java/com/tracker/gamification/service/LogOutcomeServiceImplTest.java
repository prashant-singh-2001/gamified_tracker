package com.tracker.gamification.service;

import com.tracker.gamification.dao.LogXpOutcome;
import com.tracker.gamification.dto.LogOutcomeDto;
import com.tracker.gamification.repository.LogXpOutcomeRepository;
import com.tracker.gamification.service.impl.LogOutcomeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Log Outcome Service Tests")
class LogOutcomeServiceImplTest {

    @Mock
    private LogXpOutcomeRepository logXpOutcomeRepository;

    @InjectMocks
    private LogOutcomeServiceImpl service;

    @Test
    @DisplayName("an applied log reports APPLIED with the xp and the level before and after")
    void outcomeFor_applied() {
        when(logXpOutcomeRepository.findByLogIdAndUserId(42L, 1L)).thenReturn(Optional.of(
                LogXpOutcome.builder().logId(42L).userId(1L).activityId(5L).xpApplied(120.5)
                        .previousLevel(2).newLevel(3).leveledUp(true).appliedAt(LocalDateTime.now()).build()));

        LogOutcomeDto dto = service.outcomeFor(1L, 42L);

        assertEquals(LogOutcomeDto.Status.APPLIED, dto.status());
        assertEquals(120.5, dto.xpApplied(), 1e-9);
        assertEquals(true, dto.leveledUp());
        assertEquals(3, dto.newLevel());
        assertEquals(2, dto.previousLevel());
    }

    @Test
    @DisplayName("an applied log with no level-up reports leveledUp=false and previous == new")
    void outcomeFor_appliedWithoutLevelUp() {
        when(logXpOutcomeRepository.findByLogIdAndUserId(7L, 1L)).thenReturn(Optional.of(
                LogXpOutcome.builder().logId(7L).userId(1L).activityId(5L).xpApplied(10.0)
                        .previousLevel(4).newLevel(4).leveledUp(false).appliedAt(LocalDateTime.now()).build()));

        LogOutcomeDto dto = service.outcomeFor(1L, 7L);

        assertEquals(LogOutcomeDto.Status.APPLIED, dto.status());
        assertEquals(false, dto.leveledUp());
        assertEquals(dto.previousLevel(), dto.newLevel());
    }

    @Test
    @DisplayName("no outcome row (not applied yet, unknown, or someone else's) is PENDING with null details")
    void outcomeFor_pendingWhenThereIsNoRowForThisCaller() {
        when(logXpOutcomeRepository.findByLogIdAndUserId(42L, 2L)).thenReturn(Optional.empty());

        LogOutcomeDto dto = service.outcomeFor(2L, 42L);

        assertEquals(LogOutcomeDto.Status.PENDING, dto.status());
        assertNull(dto.xpApplied());
        assertNull(dto.leveledUp());
        assertNull(dto.newLevel());
        assertNull(dto.previousLevel());
    }
}
