package com.tracker.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteActivityRequest(
        @NotBlank(message = "name cannot be null")
        String name
) {
}
