package com.tracker.gamification.service;

import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;

import java.util.List;

public interface LevelTrackerService {

    List<LevelTrackerDto> findByUserId(Long userId);

    List<LevelTrackerDto> findByActivityId(Long activityId);

    LevelTrackerDto findById(Long id);

    List<LevelTrackerDto> findAll();

    LevelTrackerDto save(LevelTrackerRequestDTO dto);
}
