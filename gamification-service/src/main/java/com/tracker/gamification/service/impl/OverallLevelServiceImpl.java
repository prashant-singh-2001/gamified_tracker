package com.tracker.gamification.service.impl;

import com.tracker.gamification.repository.OverallLevelThresholdRepository;
import com.tracker.gamification.service.OverallLevelService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

// Centralizes the XP -> overall level rule so the recompute batch and any future live update
// (e.g. LevelTrackerServiceImpl.save) can share identical logic instead of drifting apart.
@Service
@AllArgsConstructor
public class OverallLevelServiceImpl implements OverallLevelService {

    private final OverallLevelThresholdRepository overallLevelThresholdRepository;

    @Override
    public int overallLevelFor(double totalXp) {
        List<Integer> reached = overallLevelThresholdRepository.findReachedLevel(totalXp, PageRequest.of(0, 1));
        return reached.isEmpty() ? 1 : reached.get(0);
    }
}
