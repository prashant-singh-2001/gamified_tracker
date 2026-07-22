package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dao.LevelTrackerArchive;
import com.tracker.gamification.dao.LevelUpEvent;
import com.tracker.gamification.domain.LevelOutcome;
import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.repository.*;
import com.tracker.gamification.service.LevelTrackerService;
import com.tracker.gamification.service.OverallLevelService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@AllArgsConstructor
@Service
@Transactional
public class LevelTrackerServiceImpl implements LevelTrackerService {

    private final LevelTrackerRepository levelTrackerRepository;
    private final ActivityLevelThresholdRepository activityLevelThresholdRepository;
    private final LevelTrackerArchiveRepository levelTrackerArchiveRepository;
    private final LevelUpEventRepository levelUpEventRepository;
    private final OverallLevelService overallLevelService;

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

    // IDOR fix: userId now passed explicitly from the trusted header, not read off the DTO.
    // @Override
    // public LevelTrackerDto save(LevelTrackerRequestDTO dto) {
    //     boolean created = levelTrackerRepository.insertIfAbsent(dto.userId(), dto.activityId()) == 1;
    //
    //     var tracker = levelTrackerRepository
    //             .findByUserIdAndActivityIdForUpdate(dto.userId(), dto.activityId())
    //             .orElseThrow(); // guaranteed to exist; row is now locked for the rest of this transaction
    //
    //     if (!created) {
    //         archivePreviousState(tracker);
    //     }
    //
    //     tracker.setTotalXp(tracker.getTotalXp() + dto.xp());
    //     applyLevel(tracker);
    //
    //     return mapToDto(levelTrackerRepository.save(tracker));
    // }
    @Override
    public LevelTrackerDto save(Long userId, LevelTrackerRequestDTO dto) {
        boolean created = levelTrackerRepository.insertIfAbsent(userId, dto.activityId()) == 1;

        var tracker = levelTrackerRepository
                .findByUserIdAndActivityIdForUpdate(userId, dto.activityId())
                .orElseThrow(); // guaranteed to exist; row is now locked for the rest of this transaction

        if (!created) {
            archivePreviousState(tracker);
        }

        Integer oldLevel = tracker.getLevel();

        tracker.setTotalXp(tracker.getTotalXp() + dto.xp());
        tracker.setLogCount(tracker.getLogCount() + 1);
        boolean leveledUp = applyLevel(tracker);

        var saved = levelTrackerRepository.save(tracker);

        if (leveledUp) {
            levelUpEventRepository.save(LevelUpEvent.builder()
                    .userId(tracker.getUserId())
                    .activityId(tracker.getActivityId())
                    .oldLevel(oldLevel)
                    .newLevel(saved.getLevel())
                    .currentLevelXp(saved.getCurrentLevelXp())
                    .totalXp(saved.getTotalXp())
                    .createdAt(LocalDateTime.now())
                    .read(false)
                    .build());
        }

        return mapToDto(saved, leveledUp);
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

    private boolean applyLevel(LevelTracker levelTracker) {
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

        boolean leveledUp = false;
        if (outcome instanceof LevelOutcome.LeveledUp up) {
            levelTracker.setLevel(up.level());
            levelTracker.setCurrentLevelXp(up.currentLevelXp());
            leveledUp = true;
        } else if (outcome instanceof LevelOutcome.InProgress ip) {
            levelTracker.setLevel(ip.level());
            levelTracker.setCurrentLevelXp(ip.currentLevelXp());
            leveledUp = false;
        }

        return leveledUp;
    }

    private LevelTrackerDto mapToDto(LevelTracker entity, boolean leveledUp) {
        return new LevelTrackerDto(
                entity.getUserId(),
                entity.getActivityId(),
                entity.getLevel(),
                entity.getTotalXp(),
                entity.getCurrentLevelXp(),
                leveledUp
        );
    }

    // Not yet called from save() — wiring live overall-level feedback into the XP transaction
    // (TODO §B.7) needs a partial UserRank update (totalXp + overallLevel only, leaving
    // rank/percentile for the next batch), which is a separate decision from this delegation fix.
    int overallLevelFor(double totalXp) {
        return overallLevelService.overallLevelFor(totalXp);
    }

    private LevelTrackerDto mapToDto(LevelTracker entity) {
        return mapToDto(entity, false);
    }
}
