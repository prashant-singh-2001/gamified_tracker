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

            long totalDuration = categoryLogs.stream()
                    .mapToLong(l -> l.getDurationMinutes() != null ? l.getDurationMinutes() : 0L)
                    .sum();

            double totalXp = categoryLogs.stream()
                    .mapToDouble(ActivityLog::getXpEarned)
                    .sum();

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

            double dayXp = dayLogs.stream()
                    .mapToDouble(ActivityLog::getXpEarned)
                    .sum();

            long dayDuration = dayLogs.stream()
                    .mapToLong(l -> l.getDurationMinutes() != null ? l.getDurationMinutes() : 0L)
                    .sum();

            result.add(new DailyXpResponse(date, dayXp, dayDuration));
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

        double currentWeekXp = currentWeekLogs.stream()
                .mapToDouble(ActivityLog::getXpEarned)
                .sum();

        double previousWeekXp = previousWeekLogs.stream()
                .mapToDouble(ActivityLog::getXpEarned)
                .sum();

        double percentageChange;
        if (previousWeekXp == 0.0) {
            percentageChange = currentWeekXp > 0.0 ? 100.0 : 0.0;
        } else {
            percentageChange = ((currentWeekXp - previousWeekXp) / previousWeekXp) * 100.0;
        }

        long totalActiveMinutes = currentWeekLogs.stream()
                .mapToLong(l -> l.getDurationMinutes() != null ? l.getDurationMinutes() : 0L)
                .sum();

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
            double xp = dayLogs.stream().mapToDouble(ActivityLog::getXpEarned).sum();
            long duration = dayLogs.stream().mapToLong(l -> l.getDurationMinutes() != null ? l.getDurationMinutes() : 0L).sum();
            dailyBreakdown.add(new DailyXpResponse(date, xp, duration));
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

        Map<Integer, List<ActivityLog>> groupedByHour = logs.stream()
                .filter(l -> l.getStartTime() != null)
                .collect(Collectors.groupingBy(l -> l.getStartTime().getHour()));

        List<HourOfDayXpResponse> hourlyBreakdown = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            List<ActivityLog> hourLogs = groupedByHour.getOrDefault(hour, List.of());

            double hourXp = hourLogs.stream()
                    .mapToDouble(ActivityLog::getXpEarned)
                    .sum();

            long hourDuration = hourLogs.stream()
                    .mapToLong(l -> l.getDurationMinutes() != null ? l.getDurationMinutes() : 0L)
                    .sum();

            hourlyBreakdown.add(new HourOfDayXpResponse(hour, hourDuration, hourXp, (long) hourLogs.size()));
        }

        Integer bestHour = hourlyBreakdown.stream()
                .max(Comparator.comparingDouble(HourOfDayXpResponse::totalXpEarned))
                .filter(h -> h.totalXpEarned() > 0.0)
                .map(HourOfDayXpResponse::hour)
                .orElse(null);

        // Mirrors getWeeklyReport's topCategory derivation exactly (group by category, sum XP,
        // take the max) -- same tie-break behavior (Map iteration order on an exact tie), same
        // null-when-no-logs shape.
        Category bestCategory = logs.stream()
                .filter(l -> l.getActivity() != null && l.getActivity().getCategory() != null)
                .collect(Collectors.groupingBy(l -> l.getActivity().getCategory(), Collectors.summingDouble(ActivityLog::getXpEarned)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Integer bestCategoryHour = null;
        if (bestCategory != null) {
            bestCategoryHour = logs.stream()
                    .filter(l -> l.getStartTime() != null && l.getActivity() != null
                            && l.getActivity().getCategory() == bestCategory)
                    .collect(Collectors.groupingBy(l -> l.getStartTime().getHour(), Collectors.summingDouble(ActivityLog::getXpEarned)))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
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
}
