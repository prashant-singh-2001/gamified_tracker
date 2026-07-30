package com.tracker.activity.dto;

import java.time.LocalDate;

public record DailyXpResponse(
        LocalDate date,
        Double totalXpEarned,
        Long totalDurationMinutes
) {}
