package com.tracker.gamification.service;

import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.domain.LevelOutcome;
import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.repository.LevelTrackerRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@AllArgsConstructor
@Transactional
public class LevelTrackerService {

    private final LevelTrackerRepository levelTrackerRepository;

    private final ActivityLevelThresholdRepository activityLevelThresholdRepository;

    public List<LevelTrackerDto> findByUserId(Long userId) {
        return levelTrackerRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<LevelTrackerDto> findByActivityId(Long activityId) {
        return levelTrackerRepository.findAllByActivityId(activityId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<LevelTrackerDto> findAll() {
        return levelTrackerRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public LevelTrackerDto findById(Long id) {
        var levelTracker = levelTrackerRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "LevelTracker with id: " + id + " not found"
                        )
                );

        return mapToDto(levelTracker);
    }

    @Transactional
    public LevelTrackerDto save(LevelTrackerRequestDTO dto) {

        var levelTracker = levelTrackerRepository
                .findByUserIdAndActivityId(
                        dto.userId(),
                        dto.activityId()
                )
                .map(existingTracker -> {
                    existingTracker.setTotalXp(
                            existingTracker.getTotalXp() + dto.xp()
                    );
                    return existingTracker;
                })
                .orElseGet(() -> LevelTracker.builder()
                        .userId(dto.userId())
                        .activityId(dto.activityId())
                        .totalXp(dto.xp())
                        .build());

        var reachedLevels =
                activityLevelThresholdRepository
                        .findReachedLevels(
                                levelTracker.getActivityId(),
                                levelTracker.getTotalXp(),
                                PageRequest.of(0, 1)
                        );

        LevelOutcome outcome = reachedLevels.isEmpty()
                ? new LevelOutcome.InProgress(1, levelTracker.getTotalXp())
                : new LevelOutcome.LeveledUp(
                        reachedLevels.get(0).getId().getLevel(),
                        levelTracker.getTotalXp() - reachedLevels.get(0).getXpRequired()
                );

        if (outcome instanceof LevelOutcome.LeveledUp up) {
            levelTracker.setLevel(up.level());
            levelTracker.setCurrentLevelXp(up.currentLevelXp());
        } else if (outcome instanceof LevelOutcome.InProgress ip) {
            levelTracker.setLevel(ip.level());
            levelTracker.setCurrentLevelXp(ip.currentLevelXp());
        }

        var savedLevelTracker =
                levelTrackerRepository.save(levelTracker);

        return mapToDto(savedLevelTracker);
    }

    private LevelTrackerDto mapToDto(LevelTracker entity) {
        return new LevelTrackerDto(
                entity.getUserId(),
                entity.getActivityId(),
                entity.getLevel(),
                entity.getTotalXp(),
                entity.getCurrentLevelXp()
        );
    }

    private LevelTracker mapToEntity(LevelTrackerDto dto) {

        return LevelTracker.builder()
                .userId(dto.userId())
                .activityId(dto.activityId())
                .level(dto.level())
                .currentLevelXp(dto.currentLevelXp())
                .build();
    }
}
