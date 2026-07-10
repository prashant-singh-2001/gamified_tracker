package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dao.LevelTrackerArchive;
import com.tracker.gamification.domain.LevelOutcome;
import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.repository.LevelTrackerArchiveRepository;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.service.LevelTrackerService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class LevelTrackerServiceImpl implements LevelTrackerService {

    private final LevelTrackerRepository levelTrackerRepository;
    private final ActivityLevelThresholdRepository activityLevelThresholdRepository;
    private final LevelTrackerArchiveRepository levelTrackerArchiveRepository;

    public LevelTrackerServiceImpl(LevelTrackerRepository levelTrackerRepository,
                                    ActivityLevelThresholdRepository activityLevelThresholdRepository,
                                    LevelTrackerArchiveRepository levelTrackerArchiveRepository) {
        this.levelTrackerRepository = levelTrackerRepository;
        this.activityLevelThresholdRepository = activityLevelThresholdRepository;
        this.levelTrackerArchiveRepository = levelTrackerArchiveRepository;
    }

    @Override
    public List<LevelTrackerDto> findByUserId(Long userId) {
        return levelTrackerRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<LevelTrackerDto> findByActivityId(Long activityId) {
        return levelTrackerRepository.findAllByActivityId(activityId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<LevelTrackerDto> findAll() {
        return levelTrackerRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public LevelTrackerDto findById(Long id) {
        var levelTracker = levelTrackerRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "LevelTracker with id: " + id + " not found"
                        )
                );

        return mapToDto(levelTracker);
    }

    @Override
    public LevelTrackerDto save(LevelTrackerRequestDTO dto) {
        boolean created = levelTrackerRepository.insertIfAbsent(dto.userId(), dto.activityId()) == 1;

        var tracker = levelTrackerRepository
                .findByUserIdAndActivityIdForUpdate(dto.userId(), dto.activityId())
                .orElseThrow(); // guaranteed to exist; row is now locked for the rest of this transaction

        if (!created) {
            archivePreviousState(tracker);
        }

        tracker.setTotalXp(tracker.getTotalXp() + dto.xp());
        applyLevel(tracker);

        return mapToDto(levelTrackerRepository.save(tracker));
    }

    private void archivePreviousState(LevelTracker tracker) {
        levelTrackerArchiveRepository.save(
                LevelTrackerArchive.builder()
                        .userId(tracker.getUserId())
                        .activityId(tracker.getActivityId())
                        .level(tracker.getLevel())
                        .totalXp(tracker.getTotalXp())
                        .currentLevelXp(tracker.getCurrentLevelXp())
                        .archivedAt(LocalDateTime.now())
                        .build()
        );
    }

    private void applyLevel(LevelTracker levelTracker) {
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
}
