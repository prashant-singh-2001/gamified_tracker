package com.tracker.activity.domain;

import com.tracker.activity.exception.InvalidPagingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ActivityLogQuery (GET /activitylog/user/{id} paging, sort, filters)")
class ActivityLogQueryTest {

    private static ActivityLogQuery parse(String page, String size, String sort, String activityId, String from, String to) {
        return ActivityLogQuery.parse(page, size, sort, activityId, from, to);
    }

    private static String message(Runnable r) {
        return assertThrows(InvalidPagingException.class, r::run).getMessage();
    }

    @Test
    @DisplayName("defaults: page 0, size 20, newest first, no filters")
    void defaults() {
        ActivityLogQuery q = parse(null, null, null, null, null, null);

        assertEquals(0, q.page());
        assertEquals(20, q.size());
        assertEquals("startTime", q.sortField());
        assertEquals(Sort.Direction.DESC, q.direction());
        assertEquals("startTime,desc", q.sortDescription());
        assertNull(q.activityId());
        assertNull(q.from());
        assertNull(q.to());
    }

    @Test
    @DisplayName("blank values mean 'not supplied', not an error")
    void blankIsAbsent() {
        ActivityLogQuery q = parse("", " ", "", "", "", " ");
        assertEquals(0, q.page());
        assertEquals(20, q.size());
        assertNull(q.activityId());
    }

    @Test
    @DisplayName("every supplied value is parsed")
    void parsesEverything() {
        ActivityLogQuery q = parse("3", "50", "xpEarned,asc", "7", "2026-10-01", "2026-10-31");

        assertEquals(3, q.page());
        assertEquals(50, q.size());
        assertEquals("xpEarned,asc", q.sortDescription());
        assertEquals(7L, q.activityId());
        assertEquals(LocalDate.of(2026, 10, 1), q.from());
        assertEquals(LocalDate.of(2026, 10, 31), q.to());
    }

    @Test
    @DisplayName("size is bounded: 1..100")
    void sizeBounds() {
        assertEquals(1, parse(null, "1", null, null, null, null).size());
        assertEquals(100, parse(null, "100", null, null, null, null).size());
        assertEquals("size must be between 1 and 100 (got 0)", message(() -> parse(null, "0", null, null, null, null)));
        assertEquals("size must be between 1 and 100 (got 101)", message(() -> parse(null, "101", null, null, null, null)));
        assertEquals("size must be between 1 and 100 (got -5)", message(() -> parse(null, "-5", null, null, null, null)));
    }

    @Test
    @DisplayName("page must be a non-negative whole number")
    void pageValidation() {
        assertEquals("page must be >= 0 (got -1)", message(() -> parse("-1", null, null, null, null, null)));
        assertEquals("page must be a whole number (got 'abc')", message(() -> parse("abc", null, null, null, null, null)));
        assertEquals("page must be a whole number (got '1.5')", message(() -> parse("1.5", null, null, null, null, null)));
    }

    @Test
    @DisplayName("sort: a field alone is ascending (Spring's convention); direction is case-insensitive")
    void sortDirections() {
        assertEquals("createdAt,asc", parse(null, null, "createdAt", null, null, null).sortDescription());
        assertEquals("createdAt,desc", parse(null, null, "createdAt,DESC", null, null, null).sortDescription());
        assertEquals("durationMinutes,asc", parse(null, null, " durationMinutes , Asc ", null, null, null).sortDescription());
    }

    @Test
    @DisplayName("sort: only whitelisted fields; a typo or a non-column cannot reach the query layer")
    void sortWhitelist() {
        for (String field : new String[]{"startTime", "endTime", "createdAt", "xpEarned", "durationMinutes"}) {
            assertEquals(field, parse(null, null, field, null, null, null).sortField());
        }
        String msg = message(() -> parse(null, null, "password", null, null, null));
        assertEquals("sort field must be one of [createdAt, durationMinutes, endTime, startTime, xpEarned] (got 'password')", msg);
        assertEquals("sort field must be one of [createdAt, durationMinutes, endTime, startTime, xpEarned] (got 'activity.id')",
                message(() -> parse(null, null, "activity.id", null, null, null)));
    }

    @Test
    @DisplayName("sort: bad direction and too many parts are rejected")
    void sortMalformed() {
        assertEquals("sort direction must be asc or desc (got 'sideways')",
                message(() -> parse(null, null, "startTime,sideways", null, null, null)));
        assertEquals("sort must be 'field' or 'field,asc|desc' (got 'startTime,asc,extra')",
                message(() -> parse(null, null, "startTime,asc,extra", null, null, null)));
    }

    @Test
    @DisplayName("filters: activityId must be numeric; dates must be ISO; from must not be after to")
    void filterValidation() {
        assertEquals("activityId must be a number (got 'abc')", message(() -> parse(null, null, null, "abc", null, null)));
        assertEquals("from must be an ISO date like 2026-10-01 (got '01/10/2026')",
                message(() -> parse(null, null, null, null, "01/10/2026", null)));
        assertEquals("to must be an ISO date like 2026-10-01 (got '2026-13-01')",
                message(() -> parse(null, null, null, null, null, "2026-13-01")));
        assertEquals("from must not be after to (got from=2026-10-31, to=2026-10-01)",
                message(() -> parse(null, null, null, null, "2026-10-31", "2026-10-01")));
    }

    @Test
    @DisplayName("from == to is a valid single-day window")
    void singleDay() {
        ActivityLogQuery q = parse(null, null, null, null, "2026-10-05", "2026-10-05");
        assertEquals(q.from(), q.to());
    }

    @Test
    @DisplayName("the pageable sorts by the chosen field, then by id in the same direction, so pages are stable")
    void pageableHasATiebreaker() {
        Pageable pageable = parse("2", "10", "xpEarned,desc", null, null, null).toPageable();

        assertEquals(2, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("xpEarned").getDirection());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
        assertEquals("xpEarned: DESC,id: DESC", pageable.getSort().toString());
    }
}
