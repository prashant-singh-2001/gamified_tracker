package com.tracker.gateway.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddActivityLogRequest {
    private Long userId;

    private String activityName;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String notes;

    private LocalDateTime createdAt;
}
