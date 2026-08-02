package com.tracker.activity.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Issue #67, layer 0: endTime must be @PastOrPresent. Previously only @NotNull, which let
// endTime = now + 10 years through and awarded effectively unbounded XP from one request.
@DisplayName("ActivityLogRequest validation (issue #67)")
class ActivityLogRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    @DisplayName("endTime in the future fails validation (closes the unbounded-XP exploit)")
    void futureEndTime_isRejected() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest(
                "Study", now.minusMinutes(30), now.plusYears(10), "notes", now);

        Set<ConstraintViolation<ActivityLogRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("endTime")));
    }

    @Test
    @DisplayName("endTime in the past or present passes validation")
    void pastOrPresentEndTime_isAccepted() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest(
                "Study", now.minusMinutes(30), now, "notes", now);

        Set<ConstraintViolation<ActivityLogRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }
}
