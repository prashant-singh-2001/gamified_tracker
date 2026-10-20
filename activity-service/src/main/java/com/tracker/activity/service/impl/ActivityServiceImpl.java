package com.tracker.activity.service.impl;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dto.ActivityDeleteResponse;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.dto.ActivityUpdateRequest;
import com.tracker.activity.exception.ActivityNameTakenException;
import com.tracker.activity.exception.InvalidActivityUpdateException;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.repository.ActivityStreakRepository;
import com.tracker.activity.service.ActivityService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ActivityServiceImpl implements ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityStreakRepository activityStreakRepository;

    public ActivityServiceImpl(ActivityRepository activityRepository,
                               ActivityLogRepository activityLogRepository,
                               ActivityStreakRepository activityStreakRepository) {
        this.activityRepository = activityRepository;
        this.activityLogRepository = activityLogRepository;
        this.activityStreakRepository = activityStreakRepository;
    }

    @Override
    public ResponseEntity<ActivityResponseRecord> getActivity(String name) {
        // #84: an inactive activity is indistinguishable from a missing one on a read -- that's
        // what "logically invisible" means for a soft delete, and it avoids leaking catalog
        // existence. Do NOT switch this to findByName + an isActive() check; the whole point is
        // that findByName stays unfiltered for the other consumers that depend on it (see
        // ActivityRepository).
        var activity = activityRepository.findByNameAndActiveTrue(name)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + name));

        return ResponseEntity.ok(mapToResponse(activity));
    }

    @Override
    public ResponseEntity<ActivityResponseRecord> getActivityByIdOrName(String digits) {
        // GET /activity/{id}. A digits-only segment is ambiguous: it is an id, but an activity can
        // legitimately be NAMED with digits ("2048"), and GET /activity/{name} predates ids. So try
        // the id first and fall back to the name -- an existing caller asking for "2048" by name
        // keeps working. If an id and another activity's name are the same digits, the id wins.
        // Both lookups are the active-only variants, for the same reason as getActivity (#84).
        var activity = parseId(digits).flatMap(activityRepository::findByIdAndActiveTrue)
                .or(() -> activityRepository.findByNameAndActiveTrue(digits))
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + digits));

        return ResponseEntity.ok(mapToResponse(activity));
    }

    // A digits-only string too long for a Long is simply not an id (it may still be a name).
    private static Optional<Long> parseId(String digits) {
        try {
            return Optional.of(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ---- ADMIN reads: the same lookups WITHOUT the active filter. ----------------------------------
    // Reachable only with ?includeInactive, which the gateway restricts to ADMIN (SecurityConfig).
    // They exist so an admin can find a deactivated activity to re-enable or rename it: the ordinary
    // reads above deliberately make inactive rows invisible (#84), so without these a deactivated
    // activity could never be brought back through the API.

    @Override
    public ResponseEntity<ActivityResponseRecord> getActivity(String name, boolean includeInactive) {
        if (!includeInactive) {
            return getActivity(name);
        }
        var activity = activityRepository.findByName(name)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + name));
        return ResponseEntity.ok(mapToResponse(activity));
    }

    @Override
    public ResponseEntity<ActivityResponseRecord> getActivityByIdOrName(String digits, boolean includeInactive) {
        if (!includeInactive) {
            return getActivityByIdOrName(digits);
        }
        // Same id-then-name rule as the active-only read, over every row.
        var activity = parseId(digits).flatMap(activityRepository::findById)
                .or(() -> activityRepository.findByName(digits))
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + digits));
        return ResponseEntity.ok(mapToResponse(activity));
    }

    @Override
    public ResponseEntity<List<ActivityResponseRecord>> getAllActivities(boolean includeInactive) {
        if (!includeInactive) {
            return getAllActivities();
        }
        var all = activityRepository.findAll(Sort.by("id")).stream().map(this::mapToResponse).toList();
        return ResponseEntity.ok(all);
    }

    @Override
    public ResponseEntity<ActivityResponseRecord> addActivityEntity(ActivityRequestRecord request) {
        var activity = mapToActivity(request);
        // Was a bare 500 from the unique constraint; the same 409 PATCH gives for a rename.
        ensureNameFree(activity.getName(), null);

        try {
            var savedActivity = activityRepository.save(activity);
            return ResponseEntity.ok(mapToResponse(savedActivity));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent create of the same name.
            throw nameTakenOrRethrow(activity.getName(), null, e);
        }
    }

    // PATCH: only the supplied fields change. Reads UNFILTERED (findById) on purpose -- an admin has to
    // be able to edit, and above all re-enable, a deactivated activity.
    @Override
    @Transactional
    public ResponseEntity<ActivityResponseRecord> updateActivity(Long id, ActivityUpdateRequest request) {
        if (request.isEmpty()) {
            throw new InvalidActivityUpdateException(
                    "Nothing to update: supply at least one of name, category, xpMultiplier, description, active");
        }
        var activity = activityRepository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + id));

        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.equals(activity.getName())) {
                ensureNameFree(name, id);
                activity.setName(name);
            }
        }
        if (request.category() != null) {
            activity.setCategory(request.category());
        }
        if (request.xpMultiplier() != null) {
            // 0 clears the override, so the category base applies again (see Activity.effectiveXpMultiplier)
            activity.setXpMultiplier(request.xpMultiplier());
        }
        if (request.description() != null) {
            activity.setDescription(request.description());
        }
        if (request.active() != null) {
            activity.setActive(request.active());
        }

        try {
            return ResponseEntity.ok(mapToResponse(activityRepository.saveAndFlush(activity)));
        } catch (DataIntegrityViolationException e) {
            throw nameTakenOrRethrow(activity.getName(), id, e);
        }
    }

    // DELETE. If anything references the activity (a log or a streak, both FKs) it is only DEACTIVATED:
    // history keeps its activity and the name stays reserved. If nothing does, the row is REMOVED and
    // the name is free again -- which is what lets an admin undo a typo'd create. Gamification keeps
    // per-activity thresholds keyed by this id; an unreferenced activity's thresholds, if any, are
    // not touched.
    @Override
    @Transactional
    public ResponseEntity<ActivityDeleteResponse> deleteActivity(Long id) {
        var activity = activityRepository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + id));

        boolean inUse = activityLogRepository.existsByActivityId(id) || activityStreakRepository.existsByActivityId(id);
        if (inUse) {
            if (activity.isActive()) {
                activity.setActive(false);
                activityRepository.save(activity);
            }
            return ResponseEntity.ok(new ActivityDeleteResponse(id, activity.getName(), ActivityDeleteResponse.Result.DEACTIVATED));
        }

        activityRepository.delete(activity);
        return ResponseEntity.ok(new ActivityDeleteResponse(id, activity.getName(), ActivityDeleteResponse.Result.REMOVED));
    }

    /** 409 if ANOTHER activity (not {@code exceptId}) already has this exact name. */
    private void ensureNameFree(String name, Long exceptId) {
        activityRepository.findByName(name)
                .filter(other -> !other.getId().equals(exceptId))
                .ifPresent(other -> {
                    throw new ActivityNameTakenException(name);
                });
    }

    // A constraint violation is only "name taken" if the name really is taken by someone else;
    // otherwise it is some other integrity failure and must surface as itself.
    private RuntimeException nameTakenOrRethrow(String name, Long exceptId, DataIntegrityViolationException e) {
        boolean taken = activityRepository.findByName(name).filter(other -> !other.getId().equals(exceptId)).isPresent();
        return taken ? new ActivityNameTakenException(name) : e;
    }

    private Activity mapToActivity(ActivityRequestRecord activityRequest) {
        return Activity.builder()
                .name(activityRequest.name())
                .category(activityRequest.category())
                .description(activityRequest.description())
                .xpMultiplier(activityRequest.xpMultiplier())
                // #84: active is now Boolean, not boolean -- omitting the field from the request
                // JSON must mean "active" (the ordinary case), not "silently create an invisible
                // activity nobody can ever see again" (no PUT/PATCH/DELETE exists to fix it).
                .active(activityRequest.active() == null || activityRequest.active())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ActivityResponseRecord mapToResponse(Activity activity) {
        return new ActivityResponseRecord(
                activity.getId(),
                activity.getName(),
                activity.getCategory(),
                // Report the multiplier that will actually apply (#10): the per-activity override,
                // or the Category base when there's no override — never a misleading 0.0.
                activity.effectiveXpMultiplier(),
                activity.isActive(),
                activity.getDescription(),
                activity.getCreatedAt()
        );
    }

    @Override
    public ResponseEntity<List<ActivityResponseRecord>> getAllActivities() {
        // #84: same reasoning as getActivity above -- inactive activities are logically deleted,
        // so the catalog listing must not include them.
        var activities = activityRepository.findAllByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(activities);
    }
}
