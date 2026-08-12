package com.tracker.activity.exception;

import com.tracker.activity.dao.Category;
import com.tracker.activity.domain.MatchField;
import com.tracker.activity.dto.ActivitySuggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Issue #66: the {@code requestedName}/{@code suggestions} RFC 7807 extension members are now a
 * wire contract that other services and the Postman collection depend on, so the handler that
 * produces them gets a dedicated test rather than only indirect coverage via the service layer.
 */
@DisplayName("GlobalExceptionHandler (issue #66)")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ActivityNameUnresolvedException produces a 404 ProblemDetail carrying requestedName and suggestions")
    void unresolvedActivityName_carriesSuggestionsAsExtensionMembers() {
        List<ActivitySuggestion> suggestions = List.of(
                new ActivitySuggestion("Running", Category.HEALTH, true, 0.589, MatchField.DESCRIPTION));
        var ex = new ActivityNameUnresolvedException("morning jog", suggestions);

        ProblemDetail problemDetail = handler.handleUnresolvedActivityName(ex);

        assertEquals(404, problemDetail.getStatus());
        assertEquals("Activity not found: morning jog", problemDetail.getDetail());
        assertEquals("morning jog", problemDetail.getProperties().get("requestedName"));
        assertSame(suggestions, problemDetail.getProperties().get("suggestions"));
    }

    @Test
    @DisplayName("an unresolved name with no suggestions still carries an empty (not null/absent) suggestions list")
    void unresolvedActivityName_withNoSuggestions_stillCarriesEmptyList() {
        var ex = new ActivityNameUnresolvedException("quantum physics homework", List.of());

        ProblemDetail problemDetail = handler.handleUnresolvedActivityName(ex);

        assertEquals(List.of(), problemDetail.getProperties().get("suggestions"));
    }

    @Test
    @DisplayName("a plain ActivityNotFoundException still routes through handleNotFound with no extension members")
    void plainActivityNotFound_hasNoExtensionMembers() {
        var ex = new ActivityNotFoundException("Activity not found: Study");

        ProblemDetail problemDetail = handler.handleNotFound(ex);

        assertEquals(404, problemDetail.getStatus());
        assertEquals("Activity not found: Study", problemDetail.getDetail());
        assertNull(problemDetail.getProperties());
    }
}
