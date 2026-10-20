package com.tracker.gamification.service;

import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.dto.ManualXpAwardRequest;

import java.util.List;

public interface LevelTrackerService {

    // #76/#88: callerUserId is the trusted header identity; userId is the path subject being
    // requested. Throws OwnershipViolationException when they differ -- see that type's javadoc.
    List<LevelTrackerDto> findByUserId(Long callerUserId, Long userId);

    List<LevelTrackerDto> findByActivityId(Long activityId);

    // #77/#88: ownership-scoped by callerUserId. A row owned by someone else renders identically
    // to a missing row (404) -- see LevelTrackerRepository.findByIdAndUserId's javadoc.
    LevelTrackerDto findById(Long callerUserId, Long id);

    List<LevelTrackerDto> findAll();

    // IDOR fix: userId now passed explicitly from the trusted header, not read off the DTO.
    // LevelTrackerDto save(LevelTrackerRequestDTO dto);

    LevelTrackerDto save(Long userId, LevelTrackerRequestDTO dto);

    // The event-driven path (ActivityLoggedListener): same XP application as save(), plus the
    // per-log outcome row (xp applied, activity level before/after) written in the same
    // transaction -- it is what GET /activitylog/{id}/outcome serves.
    LevelTrackerDto saveForLog(Long logId, Long userId, LevelTrackerRequestDTO dto);

    // #74: the only production caller left for save() directly is ActivityLoggedListener.
    // POST /level (admin-only, see SecurityConfig) now goes through this instead, which
    // records who awarded what to whom before delegating to the same save() primitive.
    LevelTrackerDto awardManually(Long actorUserId, ManualXpAwardRequest request);
}
