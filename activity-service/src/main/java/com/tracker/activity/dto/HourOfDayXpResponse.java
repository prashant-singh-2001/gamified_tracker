package com.tracker.activity.dto;

public record HourOfDayXpResponse(
        Integer hour,
        Long totalDurationMinutes,
        Double totalXpEarned,
        Long totalSessions
) {}
