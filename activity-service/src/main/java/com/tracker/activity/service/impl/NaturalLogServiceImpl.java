package com.tracker.activity.service.impl;

import com.tracker.activity.config.NaturalLogProperties;
import com.tracker.activity.domain.LogIntentResolver;
import com.tracker.activity.domain.NaturalLanguageLogParser;
import com.tracker.activity.domain.ParsedLogIntent;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityNameResolution;
import com.tracker.activity.dto.ActivitySuggestion;
import com.tracker.activity.dto.DraftStatus;
import com.tracker.activity.dto.NaturalLogDraftResponse;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.service.ActivityNameResolutionService;
import com.tracker.activity.service.NaturalLogService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Service
public class NaturalLogServiceImpl implements NaturalLogService {

    private static final Logger log = LoggerFactory.getLogger(NaturalLogServiceImpl.class);
    private static final String METRIC_NAME = "activity.log.natural.parse";

    private final NaturalLanguageLogParser naturalLanguageLogParser;
    private final LogIntentResolver logIntentResolver;
    private final NaturalLogProperties naturalLogProperties;
    // Reused only to preview name resolution the same way the eventual POST /activitylog/ commit
    // will (issue #66) -- this service never calls ActivityLogService and never writes a log itself.
    private final ActivityRepository activityRepository;
    private final ActivityNameResolutionService activityNameResolutionService;
    private final MeterRegistry meterRegistry;

    @Override
    public ResponseEntity<NaturalLogDraftResponse> parseNaturalLog(Long userId, String text) {
        Optional<ParsedLogIntent> parsed;
        try {
            parsed = naturalLanguageLogParser.parse(text);
        } catch (Exception e) {
            // Never let a model outage turn this POST into a 500 -- GlobalExceptionHandler has no
            // catch-all handler, mirrors InsightsServiceImpl's narrator try/catch (#65).
            log.warn("Natural-language log parser failed for user {} (falling back to UNAVAILABLE)", userId, e);
            parsed = Optional.empty();
        }

        if (parsed.isEmpty()) {
            // Same disambiguation InsightsServiceImpl uses for NarrativeStatus (#65): an empty
            // result means either the flag is off or a configured backend failed to answer, and
            // naturalLogProperties.enabled() is what tells the two apart.
            DraftStatus status = naturalLogProperties.enabled() ? DraftStatus.UNAVAILABLE : DraftStatus.DISABLED;
            meterRegistry.counter(METRIC_NAME, "outcome", status.name()).increment();
            return ResponseEntity.ok(new NaturalLogDraftResponse(null, null, status, null, List.of()));
        }

        LogIntentResolver.Resolution resolution = logIntentResolver.resolve(parsed.get());
        if (!resolution.resolved()) {
            meterRegistry.counter(METRIC_NAME, "outcome", DraftStatus.NEEDS_CLARIFICATION.name()).increment();
            String interpretation = clarificationMessage(resolution.reason());
            return ResponseEntity.ok(new NaturalLogDraftResponse(
                    null, interpretation, DraftStatus.NEEDS_CLARIFICATION, null, List.of()));
        }

        ActivityLogRequest draft = resolution.draft();
        NamePreview preview = previewNameResolution(draft.activityName());

        meterRegistry.counter(METRIC_NAME, "outcome", DraftStatus.PARSED.name()).increment();
        return ResponseEntity.ok(new NaturalLogDraftResponse(
                draft, buildInterpretation(draft), DraftStatus.PARSED,
                preview.nameResolution(), preview.suggestions()));
    }

    /**
     * Previews what {@code POST /activitylog/}'s own exact-then-fuzzy resolution (issue #66) would
     * do with this name, WITHOUT rewriting {@code draft.activityName} -- the draft stays exactly
     * what the user would actually submit. Deliberately replays the same exact-match-first order as
     * {@code ActivityLogServiceImpl.resolveActivity}, since {@link ActivityNameResolutionService} is
     * documented to only be called on a miss.
     */
    private NamePreview previewNameResolution(String activityName) {
        if (activityRepository.findByName(activityName).isPresent()) {
            return new NamePreview(null, List.of());
        }
        var resolution = activityNameResolutionService.resolve(activityName);
        ActivityNameResolution nameResolution = resolution.resolved()
                ? new ActivityNameResolution(activityName, resolution.activity().getName(), resolution.score())
                : null;
        return new NamePreview(nameResolution, resolution.suggestions());
    }

    private String buildInterpretation(ActivityLogRequest draft) {
        long minutes = Duration.between(draft.startTime(), draft.endTime()).toMinutes();
        return "%s for %d minute%s (%s to %s)".formatted(
                draft.activityName(), minutes, minutes == 1 ? "" : "s",
                draft.startTime(), draft.endTime());
    }

    private String clarificationMessage(LogIntentResolver.Reason reason) {
        return switch (reason) {
            case MISSING_ACTIVITY_NAME -> "Couldn't tell what activity this was -- try naming it directly.";
            case MISSING_DURATION -> "Couldn't tell how long this took -- try including a duration.";
            case DURATION_TOO_LONG -> "That duration looks too long for a single session -- try a shorter one.";
            case FUTURE_DAY -> "That sounds like something upcoming -- you can only log something already done.";
            case RESOLVED -> throw new IllegalStateException("RESOLVED must not reach clarificationMessage");
        };
    }

    private record NamePreview(ActivityNameResolution nameResolution, List<ActivitySuggestion> suggestions) {
    }
}
