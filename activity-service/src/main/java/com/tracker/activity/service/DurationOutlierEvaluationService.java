package com.tracker.activity.service;

import com.tracker.activity.config.SessionIntegrityProperties;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.DurationOutlierDetector;
import com.tracker.activity.repository.ActivityLogRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Session integrity (#67) layer 2: resolves the duration baseline (per-user, falling back to
 * per-category cold-start) and runs the outlier detector. Shared by the log-creation path and the
 * admin review queue, which recomputes the verdict to show a maintainer why a log was flagged.
 */
@Service
@AllArgsConstructor
public class DurationOutlierEvaluationService {

    // SECURITY: only CLEARED and APPROVED logs may form the baseline — see
    // ActivityLogRepository's findRecentDurationsFor* Javadoc for the poisoning rationale.
    private static final Set<ReviewStatus> BASELINE_STATUSES = EnumSet.of(ReviewStatus.CLEARED, ReviewStatus.APPROVED);

    private final ActivityLogRepository activityLogRepository;
    private final DurationOutlierDetector durationOutlierDetector;
    private final SessionIntegrityProperties sessionIntegrityProperties;

    public DurationOutlierDetector.Verdict evaluate(Long userId, Category category, long durationMinutes) {
        // Closes the self-consistency hole: a user who cold-start-seeds their baseline at 1440
        // min/session, or ratchets up in small steps, is never an outlier against their OWN
        // history (the detector below is one-sided on the user's baseline). This check is
        // independent of it, so checked first and short-circuits without touching the database.
        if (durationMinutes > sessionIntegrityProperties.absoluteFlagMinutes()) {
            return new DurationOutlierDetector.Verdict(true, 0.0, 0.0, 0, DurationOutlierDetector.Basis.ABSOLUTE_THRESHOLD);
        }

        Pageable window = PageRequest.of(0, sessionIntegrityProperties.baselineWindow());

        List<Long> baseline = activityLogRepository.findRecentDurationsForUserAndCategory(
                userId, category, BASELINE_STATUSES, window);
        if (baseline.size() < sessionIntegrityProperties.minSamples()) {
            baseline = activityLogRepository.findRecentDurationsForCategory(category, BASELINE_STATUSES, window);
        }

        return durationOutlierDetector.evaluate(durationMinutes, baseline);
    }
}
