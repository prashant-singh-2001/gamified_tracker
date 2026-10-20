package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /activity/{id}: every field optional, and an ABSENT field means "leave it alone". At least
 * one must be present.
 *
 * <p>All are boxed so absent is distinguishable from a value: that is why {@code active} is
 * {@code Boolean} and {@code xpMultiplier} is {@code Double}.
 *
 * @param name         trimmed; must stay unique (409 if another activity already has it)
 * @param xpMultiplier {@code 0} clears the per-activity override, so the {@link Category} base applies
 *                     again (the entity's documented meaning of a non-positive value); negative is rejected
 * @param description  an empty string clears it
 * @param active       {@code false} hides the activity from the catalog and refuses new logs against it;
 *                     {@code true} brings it back
 */
public record ActivityUpdateRequest(
        @Size(max = 255, message = "name must be at most 255 characters")
        @Pattern(regexp = ".*\\S.*", message = "name must not be blank")
        String name,
        Category category,
        @PositiveOrZero(message = "xpMultiplier cannot be negative (use 0 to fall back to the category base)")
        Double xpMultiplier,
        String description,
        Boolean active
) {
    public boolean isEmpty() {
        return name == null && category == null && xpMultiplier == null && description == null && active == null;
    }
}
