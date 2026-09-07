package com.tracker.activity.service.impl;

import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.BestTimeOfDayResponse;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.HourOfDayXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.exception.OwnershipViolationException;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ActivityLogRepository activityLogRepository;

    @Autowired
    public AnalyticsServiceImpl(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    public ResponseEntity<List<CategorySummaryResponse>> getCategorySummary(Long callerUserId, Long userId) {
        requireSelf(callerUserId, userId);
        List<ActivityLog> logs = activityLogRepository.findByUserId(userId);

        Map<Category, List<ActivityLog>> grouped = logs.stream()
                .filter(log -> log.getActivity() != null && log.getActivity().getCategory() != null)
                .collect(Collectors.groupingBy(log -> log.getActivity().getCategory()));

        List<CategorySummaryResponse> summaries = new ArrayList<>();

        for (Map.Entry<Category, List<ActivityLog>> entry : grouped.entrySet()) {
            Category category = entry.getKey();
            List<ActivityLog> categoryLogs = entry.getValue();

            long totalDuration = sumDurationMinutes(categoryLogs);
            double totalXp = sumXp(categoryLogs);
            long totalSessions = categoryLogs.size();

            summaries.add(new CategorySummaryResponse(category, totalDuration, totalXp, totalSessions));
        }

        return ResponseEntity.ok(summaries);
    }

    @Override
    public ResponseEntity<List<DailyXpResponse>> getXpOverTime(Long callerUserId, Long userId, int days) {
        requireSelf(callerUserId, userId);
        int rangeDays = Math.max(days, 1);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(rangeDays - 1);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<ActivityLog> logs = activityLogRepository.findByUserIdAndStartTimeBetween(userId, startDateTime, endDateTime);

        Map<LocalDate, List<ActivityLog>> groupedByDate = logs.stream()
                .filter(l -> l.getStartTime() != null)
                .collect(Collectors.groupingBy(l -> l.getStartTime().toLocalDate()));

        List<DailyXpResponse> result = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<ActivityLog> dayLogs = groupedByDate.getOrDefault(date, List.of());
            result.add(new DailyXpResponse(date, sumXp(dayLogs), sumDurationMinutes(dayLogs)));
        }

        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<WeeklyReportResponse> getWeeklyReport(Long callerUserId, Long userId) {
        requireSelf(callerUserId, userId);
        LocalDate today = LocalDate.now();
        LocalDate currentWeekStart = today.minusDays(6);
        LocalDate previousWeekStart = currentWeekStart.minusDays(7);

        LocalDateTime prevStart = previousWeekStart.atStartOfDay();
        LocalDateTime currentStart = currentWeekStart.atStartOfDay();
        LocalDateTime endNow = today.atTime(LocalTime.MAX);

        List<ActivityLog> logs = activityLogRepository.findByUserIdAndStartTimeBetween(userId, prevStart, endNow);

        List<ActivityLog> currentWeekLogs = logs.stream()
                .filter(l -> l.getStartTime() != null && !l.getStartTime().isBefore(currentStart))
                .toList();

        List<ActivityLog> previousWeekLogs = logs.stream()
                .filter(l -> l.getStartTime() != null && l.getStartTime().isBefore(currentStart))
                .toList();

        double currentWeekXp = sumXp(currentWeekLogs);
        double previousWeekXp = sumXp(previousWeekLogs);

        double percentageChange;
        if (previousWeekXp == 0.0) {
            percentageChange = currentWeekXp > 0.0 ? 100.0 : 0.0;
        } else {
            percentageChange = ((currentWeekXp - previousWeekXp) / previousWeekXp) * 100.0;
        }

        long totalActiveMinutes = sumDurationMinutes(currentWeekLogs);

        Category topCategory = currentWeekLogs.stream()
                .filter(l -> l.getActivity() != null && l.getActivity().getCategory() != null)
                .collect(Collectors.groupingBy(l -> l.getActivity().getCategory(), Collectors.summingDouble(ActivityLog::getXpEarned)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Map<LocalDate, List<ActivityLog>> currentWeekGrouped = currentWeekLogs.stream()
                .filter(l -> l.getStartTime() != null)
                .collect(Collectors.groupingBy(l -> l.getStartTime().toLocalDate()));

        List<DailyXpResponse> dailyBreakdown = new ArrayList<>();
        for (LocalDate date = currentWeekStart; !date.isAfter(today); date = date.plusDays(1)) {
            List<ActivityLog> dayLogs = currentWeekGrouped.getOrDefault(date, List.of());
            dailyBreakdown.add(new DailyXpResponse(date, sumXp(dayLogs), sumDurationMinutes(dayLogs)));
        }

        WeeklyReportResponse report = new WeeklyReportResponse(
                currentWeekXp,
                previousWeekXp,
                percentageChange,
                totalActiveMinutes,
                topCategory,
                dailyBreakdown
        );

        return ResponseEntity.ok(report);
    }

    @Override
    public ResponseEntity<BestTimeOfDayResponse> getBestTimeOfDay(Long callerUserId, Long userId) {
        requireSelf(callerUserId, userId);
        List<ActivityLog> logs = activityLogRepository.findByUserId(userId);

        // #103 review: a null startTime must exclude a row from every derived field, not just the
        // hourly buckets -- previously bestCategory alone skipped this filter, so it could name a
        // category that never appears anywhere in hourlyBreakdown.
        List<ActivityLog> timedLogs = logs.stream().filter(l -> l.getStartTime() != null).toList();

        Map<Integer, List<ActivityLog>> groupedByHour = timedLogs.stream()
                .collect(Collectors.groupingBy(l -> l.getStartTime().getHour()));

        List<HourOfDayXpResponse> hourlyBreakdown = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            List<ActivityLog> hourLogs = groupedByHour.getOrDefault(hour, List.of());
            hourlyBreakdown.add(new HourOfDayXpResponse(
                    hour, sumDurationMinutes(hourLogs), sumXp(hourLogs), (long) hourLogs.size()));
        }

        Integer bestHour = hourlyBreakdown.stream()
                .max(Comparator.comparingDouble(HourOfDayXpResponse::totalXpEarned))
                .filter(h -> h.totalXpEarned() > 0.0)
                .map(HourOfDayXpResponse::hour)
                .orElse(null);

        // #103 review: bestCategory/bestCategoryHour must go null together with bestHour -- a
        // user with zero XP everywhere has no "best" anything, not a best category paired with
        // no best hour. Both stay null unless bestHour cleared the same >0.0 bar.
        Category bestCategory = null;
        Integer bestCategoryHour = null;
        if (bestHour != null) {
            // Mirrors getWeeklyReport's topCategory derivation exactly (group by category, sum
            // XP, take the max), over the same startTime-filtered rows as hourlyBreakdown above.
            // Tie-break here is Map iteration order, same as topCategory -- deliberately NOT the
            // same rule bestHour/bestCategoryHour use (see below), since matching topCategory's
            // shape exactly is the point.
            bestCategory = timedLogs.stream()
                    .filter(l -> l.getActivity() != null && l.getActivity().getCategory() != null)
                    .collect(Collectors.groupingBy(l -> l.getActivity().getCategory(), Collectors.summingDouble(ActivityLog::getXpEarned)))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (bestCategory != null) {
                // Scanned off the already-built groupedByHour buckets in ascending hour order
                // (not a fresh grouping of the raw list), so an exact tie resolves to the
                // earliest hour -- the same deterministic rule bestHour follows.
                final Category winningCategory = bestCategory;
                double[] categoryHourXp = new double[24];
                for (int hour = 0; hour < 24; hour++) {
                    categoryHourXp[hour] = sumXp(groupedByHour.getOrDefault(hour, List.of()).stream()
                            .filter(l -> l.getActivity() != null && l.getActivity().getCategory() == winningCategory)
                            .toList());
                }
                bestCategoryHour = 0;
                for (int hour = 1; hour < 24; hour++) {
                    if (categoryHourXp[hour] > categoryHourXp[bestCategoryHour]) {
                        bestCategoryHour = hour;
                    }
                }
            }
        }

        return ResponseEntity.ok(new BestTimeOfDayResponse(hourlyBreakdown, bestHour, bestCategory, bestCategoryHour));
    }

    // #88: shared guard for all four analytics reads -- each is keyed by a userId path
    // variable with no other object to compare against, so a mismatch is always a 403.
    private void requireSelf(Long callerUserId, Long userId) {
        if (!callerUserId.equals(userId)) {
            throw new OwnershipViolationException("Not permitted to access another user's data");
        }
    }

    // #103 review: this exact idiom (XP sum, null-safe duration sum) was copy-pasted across all
    // four public methods above -- one place for it now, and one place for the eventual
    // reviewStatus filter (see the follow-up issue linked from analytics.md's honest gaps).
    private static double sumXp(List<ActivityLog> logs) {
        return logs.stream().mapToDouble(ActivityLog::getXpEarned).sum();
    }

    private static long sumDurationMinutes(List<ActivityLog> logs) {
        return logs.stream().mapToLong(l -> l.getDurationMinutes() != null ? l.getDurationMinutes() : 0L).sum();
    }
}
