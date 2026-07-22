package com.tracker.gamification.dto;

public record LeaderboardEntryDto(Long rank, Long userId, double totalXp) {
}
