package com.tracker.activity.service;

import com.tracker.activity.domain.ActivityLogQuery;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.PagedResponse;
import com.tracker.activity.dto.StreakResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ActivityLogService {
    // #78/#88: callerUserId is the trusted header identity. A row owned by someone else renders
    // identically to a missing row (404) -- see ActivityLogRepository.findByIdAndUserId's javadoc.
    ResponseEntity<ActivityLogResponse> getActivityLogResponseEntity(Long callerUserId, Long id);

    // #79/#88: callerUserId is the trusted header identity; id is the path subject being
    // requested. Throws OwnershipViolationException when they differ.
    ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(Long callerUserId, Long id);

    // The paged, sortable, filterable form of the above. Same ownership rule (403 for anyone but the
    // owner); the unpaged method stays for clients that have not switched.
    ResponseEntity<PagedResponse<ActivityLogResponse>> getActivityForUserPaged(Long callerUserId, Long id, ActivityLogQuery query);

    ResponseEntity<ActivityLogResponse> addActivityLogResponseResponseEntity(Long userId, ActivityLogRequest addActivityLogRequest);

    // #80/#88: callerUserId is the trusted header identity; userId is the path subject being
    // requested. Throws OwnershipViolationException when they differ.
    ResponseEntity<List<StreakResponse>> getStreaksForUser(Long callerUserId, Long userId);
}
