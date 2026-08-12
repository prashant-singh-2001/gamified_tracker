package com.tracker.activity.service;

import com.tracker.activity.config.ActivityNameMatchingProperties;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.domain.ActivityCandidate;
import com.tracker.activity.domain.ActivityMatcher;
import com.tracker.activity.dto.ActivitySuggestion;
import com.tracker.activity.repository.ActivityRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Issue #66: bridges the pure {@link ActivityMatcher} to the catalog. Called ONLY when the exact
 * {@code findByName} lookup misses, so the happy path pays nothing for it.
 */
@Service
@AllArgsConstructor
public class ActivityNameResolutionService {

    private final ActivityRepository activityRepository;
    private final ActivityMatcher activityMatcher;
    private final ActivityNameMatchingProperties activityNameMatchingProperties;

    public NameResolution resolve(String requestedName) {
        // Read the catalog live, per call. It is admin-curated and tiny (tens of rows), this is an
        // error path by definition, and POST /activity can add a row at any moment -- an index
        // "computed once at startup" would be stale the instant an admin creates an activity, and
        // with N replicas each would go stale independently (no leader election, no shedlock).
        List<Activity> catalog = activityRepository.findAll();

        Map<String, Activity> byName = new LinkedHashMap<>();
        List<ActivityCandidate> candidates = new ArrayList<>(catalog.size());
        for (Activity activity : catalog) {
            String name = activity.getName();
            if (name == null || name.isBlank()) {
                continue;   // an unnamed catalog row can never be logged against
            }
            byName.putIfAbsent(name, activity);   // activity.name is UNIQUE in the schema
            candidates.add(new ActivityCandidate(
                    name,
                    activity.getDescription(),
                    activity.getCategory() != null ? activity.getCategory().name() : null,
                    activity.isActive()));
        }

        ActivityMatcher.Resolution resolution = activityMatcher.resolve(requestedName, candidates);

        List<ActivitySuggestion> suggestions = resolution.suggestions().stream()
                .map(match -> {
                    Activity activity = byName.get(match.candidate().name());
                    return new ActivitySuggestion(activity.getName(), activity.getCategory(),
                            activity.isActive(), round(match.score()), match.matchedOn());
                })
                .toList();

        if (resolution.autoResolved() == null) {
            return new NameResolution(null, 0.0, suggestions, resolution.reason());
        }
        // Kill switch: suggestions still ride the 404, only the substitution is withheld -- exactly
        // how session-integrity's outlier-detection-enabled gates layer 2 alone.
        if (!activityNameMatchingProperties.autoResolveEnabled()) {
            return new NameResolution(null, 0.0, suggestions, ActivityMatcher.Reason.DISABLED);
        }
        Activity resolved = byName.get(resolution.autoResolved().candidate().name());
        return new NameResolution(resolved, round(resolution.autoResolved().score()),
                suggestions, resolution.reason());
    }

    /** 3dp: the score is a wire value for humans, not an input to further math. */
    private static double round(double score) {
        return Math.round(score * 1000.0) / 1000.0;
    }

    public record NameResolution(Activity activity, double score,
                                 List<ActivitySuggestion> suggestions, ActivityMatcher.Reason reason) {
        public boolean resolved() {
            return activity != null;
        }
    }
}
