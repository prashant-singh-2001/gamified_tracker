package com.tracker.activity.service.impl;

import com.tracker.activity.config.InsightsProperties;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.domain.DigestFacts;
import com.tracker.activity.domain.WeeklyDigestNarrator;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.NarrativeStatus;
import com.tracker.activity.dto.WeeklyInsightsResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.AnalyticsService;
import com.tracker.activity.service.InsightsService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class InsightsServiceImpl implements InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsServiceImpl.class);

    private final ActivityLogRepository activityLogRepository;
    private final AnalyticsService analyticsService;
    private final WeeklyDigestNarrator weeklyDigestNarrator;
    private final InsightsProperties insightsProperties;
    private final MeterRegistry meterRegistry;

    @Override
    public ResponseEntity<WeeklyInsightsResponse> getWeeklyInsights(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);

        // Delegated, not recomputed: makes "insights and /weekly-report never disagree" true by
        // construction rather than by two copies of the same window math staying in sync.
        WeeklyReportResponse totals = analyticsService.getWeeklyReport(userId).getBody();

        List<ActivityLog> weekLogs = activityLogRepository.findByUserIdAndStartTimeBetween(
                userId, weekStart.atStartOfDay(), today.atTime(LocalTime.MAX));

        List<CategorySummaryResponse> categories = buildCategorySummaries(weekLogs);
        List<String> noteLines = collectNoteLines(weekLogs);

        DigestFacts facts = new DigestFacts(
                weekStart, today,
                nullToZero(totals.currentWeekXp()),
                nullToZero(totals.previousWeekXp()),
                nullToZero(totals.percentageChange()),
                totals.totalActiveMinutes() != null ? totals.totalActiveMinutes() : 0L,
                totals.topCategory(),
                categories.stream()
                        .map(c -> new DigestFacts.CategoryFacts(
                                c.category(), c.totalDurationMinutes(), c.totalXpEarned(), c.totalSessions()))
                        .toList(),
                noteLines);

        String narrative = null;
        NarrativeStatus narrativeStatus;
        try {
            Optional<String> result = weeklyDigestNarrator.narrate(facts);
            if (result.isPresent()) {
                // Bounded here, not just trusted from the narrator: any WeeklyDigestNarrator
                // implementation funnels through this one place, so the maxNarrativeChars
                // invariant on WeeklyInsightsResponse holds regardless of which one is wired in.
                narrative = boundNarrative(result.get());
                narrativeStatus = NarrativeStatus.GENERATED;
            } else {
                narrativeStatus = insightsProperties.enabled() ? NarrativeStatus.UNAVAILABLE : NarrativeStatus.DISABLED;
            }
        } catch (Exception e) {
            // Never let a model outage turn a working GET into a 500 -- GlobalExceptionHandler has
            // no catch-all handler, so an escaping exception here would surface as a raw 500.
            log.warn("Weekly digest narrator failed for user {} (falling back to numbers-only)", userId, e);
            narrativeStatus = NarrativeStatus.UNAVAILABLE;
        }
        meterRegistry.counter("activity.insights.narrative", "outcome", narrativeStatus.name()).increment();

        return ResponseEntity.ok(new WeeklyInsightsResponse(
                weekStart, today, totals, categories, narrative, narrativeStatus));
    }

    // Mirrors AnalyticsServiceImpl.getCategorySummary's grouping idiom, date-windowed to the
    // current week -- that per-category-per-week aggregate doesn't exist anywhere else.
    private List<CategorySummaryResponse> buildCategorySummaries(List<ActivityLog> weekLogs) {
        Map<Category, List<ActivityLog>> grouped = weekLogs.stream()
                .filter(l -> l.getActivity() != null && l.getActivity().getCategory() != null)
                .collect(Collectors.groupingBy(l -> l.getActivity().getCategory()));

        List<CategorySummaryResponse> summaries = new ArrayList<>();
        for (Map.Entry<Category, List<ActivityLog>> entry : grouped.entrySet()) {
            Category category = entry.getKey();
            List<ActivityLog> categoryLogs = entry.getValue();

            long totalDuration = categoryLogs.stream()
                    .mapToLong(l -> l.getDurationMinutes() != null ? l.getDurationMinutes() : 0L)
                    .sum();
            double totalXp = categoryLogs.stream()
                    .mapToDouble(ActivityLog::getXpEarned)
                    .sum();
            long totalSessions = categoryLogs.size();

            summaries.add(new CategorySummaryResponse(category, totalDuration, totalXp, totalSessions));
        }
        return summaries;
    }

    // Newest-first, per DigestFacts.noteLines()'s contract -- WeeklyDigestPromptBuilder caps to
    // maxNotes by taking the first N, so order here decides which notes survive that cap.
    private List<String> collectNoteLines(List<ActivityLog> weekLogs) {
        return weekLogs.stream()
                .filter(l -> l.getStartTime() != null && l.getNotes() != null && !l.getNotes().isBlank())
                .sorted(Comparator.comparing(ActivityLog::getStartTime).reversed())
                .map(ActivityLog::getNotes)
                .toList();
    }

    private static double nullToZero(Double value) {
        return value != null ? value : 0.0;
    }

    private String boundNarrative(String narrative) {
        if (narrative == null || narrative.length() <= insightsProperties.maxNarrativeChars()) {
            return narrative;
        }
        return narrative.substring(0, insightsProperties.maxNarrativeChars());
    }
}
