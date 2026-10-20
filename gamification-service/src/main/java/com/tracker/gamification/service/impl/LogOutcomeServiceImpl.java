package com.tracker.gamification.service.impl;

import com.tracker.gamification.dto.LogOutcomeDto;
import com.tracker.gamification.repository.LogXpOutcomeRepository;
import com.tracker.gamification.service.LogOutcomeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class LogOutcomeServiceImpl implements LogOutcomeService {

    private final LogXpOutcomeRepository logXpOutcomeRepository;

    @Override
    public LogOutcomeDto outcomeFor(Long callerUserId, Long logId) {
        return logXpOutcomeRepository.findByLogIdAndUserId(logId, callerUserId)
                .map(o -> LogOutcomeDto.applied(o.getXpApplied(), o.isLeveledUp(), o.getNewLevel(), o.getPreviousLevel()))
                .orElseGet(LogOutcomeDto::pending);
    }
}
