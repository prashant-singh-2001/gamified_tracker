package com.tracker.activity.domain;

import com.tracker.activity.exception.InvalidPagingException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * A validated request for one page of a user's activity logs: paging, sorting and filters.
 *
 * <p>Built once from the raw query parameters so every rule lives (and is tested) in one place, and
 * a bad value is a 400 naming the parameter rather than a default quietly substituted.
 *
 * @param activityId only logs for this activity, or {@code null} for all
 * @param from       first day, inclusive, by log START date (the calendar the analytics use); or {@code null}
 * @param to         last day, inclusive; or {@code null}
 */
public record ActivityLogQuery(Long activityId, LocalDate from, LocalDate to, int page, int size,
                               String sortField, Sort.Direction direction) {

    public static final int DEFAULT_SIZE = 20;
    /** Bounds one response; a client wanting more asks for the next page. */
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT_FIELD = "startTime";

    /** The only fields a client may sort by: real columns, so a typo cannot reach the query layer. */
    public static final Set<String> SORTABLE = Set.of("startTime", "endTime", "createdAt", "xpEarned", "durationMinutes");

    public static ActivityLogQuery parse(String page, String size, String sort,
                                         String activityId, String from, String to) {
        int pageIndex = parseInt("page", page, 0);
        if (pageIndex < 0) {
            throw new InvalidPagingException("page must be >= 0 (got " + pageIndex + ")");
        }
        int pageSize = parseInt("size", size, DEFAULT_SIZE);
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new InvalidPagingException("size must be between 1 and " + MAX_SIZE + " (got " + pageSize + ")");
        }

        String field = DEFAULT_SORT_FIELD;
        // Newest first by default: this is a history, and the next thing a user wants is the latest.
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            if (parts.length > 2) {
                throw new InvalidPagingException("sort must be 'field' or 'field,asc|desc' (got '" + sort + "')");
            }
            field = parts[0].trim();
            if (!SORTABLE.contains(field)) {
                throw new InvalidPagingException("sort field must be one of " + SORTABLE.stream().sorted().toList()
                        + " (got '" + field + "')");
            }
            direction = Sort.Direction.ASC; // Spring's own convention when no direction is given
            if (parts.length == 2) {
                direction = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
                    case "asc" -> Sort.Direction.ASC;
                    case "desc" -> Sort.Direction.DESC;
                    default -> throw new InvalidPagingException(
                            "sort direction must be asc or desc (got '" + parts[1] + "')");
                };
            }
        }

        Long activity = null;
        if (activityId != null && !activityId.isBlank()) {
            try {
                activity = Long.parseLong(activityId.trim());
            } catch (NumberFormatException e) {
                throw new InvalidPagingException("activityId must be a number (got '" + activityId + "')");
            }
        }

        LocalDate fromDate = parseDate("from", from);
        LocalDate toDate = parseDate("to", to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidPagingException("from must not be after to (got from=" + fromDate + ", to=" + toDate + ")");
        }

        return new ActivityLogQuery(activity, fromDate, toDate, pageIndex, pageSize, field, direction);
    }

    /**
     * The id is always a tiebreaker, in the same direction: without it, rows with an equal sort value
     * (two logs with the same start time, or equal XP) can swap between pages and a row would appear
     * twice or never.
     */
    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(direction, sortField).and(Sort.by(direction, "id")));
    }

    /** The sort as the client wrote it, for the response, e.g. {@code startTime,desc}. */
    public String sortDescription() {
        return sortField + "," + direction.name().toLowerCase(Locale.ROOT);
    }

    private static int parseInt(String name, String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidPagingException(name + " must be a whole number (got '" + raw + "')");
        }
    }

    private static LocalDate parseDate(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeException e) {
            throw new InvalidPagingException(name + " must be an ISO date like 2026-10-01 (got '" + raw + "')");
        }
    }
}
