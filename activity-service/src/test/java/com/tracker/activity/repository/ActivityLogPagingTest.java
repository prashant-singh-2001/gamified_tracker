package com.tracker.activity.repository;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.ActivityLogQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs GET /activitylog/user/{id}'s query for real (H2): the specification, the sort, the tie-break
 * and the total count only mean something against a database.
 */
@DataJpaTest
@DisplayName("Activity log paging, sorting and filtering")
class ActivityLogPagingTest {

    private static final long USER = 1L;

    @Autowired
    private ActivityLogRepository activityLogRepository;
    @Autowired
    private ActivityRepository activityRepository;

    private Activity study;
    private Activity running;

    @BeforeEach
    void seed() {
        study = activityRepository.save(Activity.builder().name("Study").category(Category.STUDY)
                .xpMultiplier(1.0).active(true).createdAt(LocalDateTime.now()).build());
        running = activityRepository.save(Activity.builder().name("Run").category(Category.HEALTH)
                .xpMultiplier(1.0).active(true).createdAt(LocalDateTime.now()).build());
    }

    private ActivityLog log(long userId, Activity activity, LocalDateTime start, long minutes, double xp) {
        return activityLogRepository.save(ActivityLog.builder()
                .userId(userId).activity(activity).startTime(start).endTime(start.plusMinutes(minutes))
                .durationMinutes(minutes).xpEarned(xp).createdAt(start.plusMinutes(minutes))
                .reviewStatus(ReviewStatus.CLEARED).build());
    }

    private Page<ActivityLog> page(long userId, ActivityLogQuery q) {
        return activityLogRepository.findAll(ActivityLogSpecifications.matching(userId, q), q.toPageable());
    }

    private static ActivityLogQuery q(String page, String size, String sort, String activityId, String from, String to) {
        return ActivityLogQuery.parse(page, size, sort, activityId, from, to);
    }

    private static List<Long> ids(Page<ActivityLog> p) {
        return p.getContent().stream().map(ActivityLog::getId).toList();
    }

    @Test
    @DisplayName("pages partition the rows: total, page count, last partial page, nothing repeated or lost")
    void pagesPartitionTheRows() {
        List<Long> all = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            all.add(log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0).plusDays(i), 30, 10.0).getId());
        }

        Page<ActivityLog> first = page(USER, q("0", "10", null, null, null, null));
        Page<ActivityLog> second = page(USER, q("1", "10", null, null, null, null));
        Page<ActivityLog> third = page(USER, q("2", "10", null, null, null, null));
        Page<ActivityLog> beyond = page(USER, q("3", "10", null, null, null, null));

        assertEquals(25, first.getTotalElements());
        assertEquals(3, first.getTotalPages());
        assertEquals(10, first.getContent().size());
        assertEquals(10, second.getContent().size());
        assertEquals(5, third.getContent().size(), "the last page is partial");
        assertTrue(beyond.getContent().isEmpty(), "a page past the end is empty, not an error");
        assertEquals(25, beyond.getTotalElements());

        Set<Long> seen = new HashSet<>();
        for (Page<ActivityLog> p : List.of(first, second, third)) {
            seen.addAll(ids(p));
        }
        assertEquals(new HashSet<>(all), seen, "every row exactly once across the pages");
    }

    @Test
    @DisplayName("default order is newest first by start time")
    void defaultIsNewestFirst() {
        ActivityLog oldest = log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0), 30, 10);
        ActivityLog newest = log(USER, study, LocalDateTime.of(2026, 10, 3, 8, 0), 30, 10);
        ActivityLog middle = log(USER, study, LocalDateTime.of(2026, 10, 2, 8, 0), 30, 10);

        assertEquals(List.of(newest.getId(), middle.getId(), oldest.getId()),
                ids(page(USER, q(null, null, null, null, null, null))));
    }

    @Test
    @DisplayName("sort by xpEarned, both directions")
    void sortByXp() {
        ActivityLog small = log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0), 30, 5);
        ActivityLog big = log(USER, study, LocalDateTime.of(2026, 10, 2, 8, 0), 30, 500);
        ActivityLog mid = log(USER, study, LocalDateTime.of(2026, 10, 3, 8, 0), 30, 50);

        assertEquals(List.of(big.getId(), mid.getId(), small.getId()),
                ids(page(USER, q(null, null, "xpEarned,desc", null, null, null))));
        assertEquals(List.of(small.getId(), mid.getId(), big.getId()),
                ids(page(USER, q(null, null, "xpEarned,asc", null, null, null))));
    }

    @Test
    @DisplayName("rows with an EQUAL sort value are ordered by id, so a page boundary never repeats or drops one")
    void tieBreakerKeepsPagesStable() {
        // Ten logs, all with the same XP: without a tie-break their order is undefined and a row
        // can appear on two pages (or none).
        for (int i = 0; i < 10; i++) {
            log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0).plusMinutes(i), 30, 100.0);
        }

        List<Long> collected = new ArrayList<>();
        for (int p = 0; p < 5; p++) {
            collected.addAll(ids(page(USER, q(String.valueOf(p), "2", "xpEarned,desc", null, null, null))));
        }

        assertEquals(10, collected.size());
        assertEquals(10, new HashSet<>(collected).size(), "no row repeated across pages");
        List<Long> descending = new ArrayList<>(collected);
        descending.sort(java.util.Comparator.reverseOrder());
        assertEquals(descending, collected, "ties broken by id in the same direction as the sort");
    }

    @Test
    @DisplayName("filter by activityId")
    void filterByActivity() {
        ActivityLog a = log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0), 30, 10);
        log(USER, running, LocalDateTime.of(2026, 10, 2, 8, 0), 30, 10);

        Page<ActivityLog> p = page(USER, q(null, null, null, String.valueOf(study.getId()), null, null));

        assertEquals(List.of(a.getId()), ids(p));
        assertEquals(1, p.getTotalElements(), "the total counts the FILTERED rows");
    }

    @Test
    @DisplayName("an unknown activityId is an empty page, not an error")
    void unknownActivityIsEmpty() {
        log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0), 30, 10);

        Page<ActivityLog> p = page(USER, q(null, null, null, "999999", null, null));

        assertTrue(p.getContent().isEmpty());
        assertEquals(0, p.getTotalElements());
        assertEquals(0, p.getTotalPages());
    }

    @Test
    @DisplayName("date filters are inclusive of both days, by START date, through the last instant of 'to'")
    void dateFiltersAreInclusive() {
        ActivityLog before = log(USER, study, LocalDateTime.of(2026, 10, 4, 23, 59, 59), 30, 1);
        ActivityLog firstInstant = log(USER, study, LocalDateTime.of(2026, 10, 5, 0, 0, 0), 30, 2);
        ActivityLog middle = log(USER, study, LocalDateTime.of(2026, 10, 6, 12, 0), 30, 3);
        ActivityLog lastInstant = log(USER, study, LocalDateTime.of(2026, 10, 7, 23, 59, 59), 30, 4);
        ActivityLog after = log(USER, study, LocalDateTime.of(2026, 10, 8, 0, 0, 0), 30, 5);

        Page<ActivityLog> p = page(USER, q(null, null, "startTime,asc", null, "2026-10-05", "2026-10-07"));

        assertEquals(List.of(firstInstant.getId(), middle.getId(), lastInstant.getId()), ids(p));
        assertTrue(!ids(p).contains(before.getId()) && !ids(p).contains(after.getId()));
    }

    @Test
    @DisplayName("from alone and to alone are open-ended")
    void openEndedDateFilters() {
        ActivityLog early = log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0), 30, 1);
        ActivityLog late = log(USER, study, LocalDateTime.of(2026, 10, 20, 8, 0), 30, 2);

        assertEquals(List.of(late.getId()), ids(page(USER, q(null, null, null, null, "2026-10-10", null))));
        assertEquals(List.of(early.getId()), ids(page(USER, q(null, null, null, null, null, "2026-10-10"))));
    }

    @Test
    @DisplayName("filters combine (AND)")
    void filtersCombine() {
        log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0), 30, 1);
        ActivityLog hit = log(USER, study, LocalDateTime.of(2026, 10, 6, 8, 0), 30, 2);
        log(USER, running, LocalDateTime.of(2026, 10, 6, 9, 0), 30, 3);

        Page<ActivityLog> p = page(USER,
                q(null, null, null, String.valueOf(study.getId()), "2026-10-05", "2026-10-07"));

        assertEquals(List.of(hit.getId()), ids(p));
    }

    @Test
    @DisplayName("another user's logs never appear -- ownership is part of the query, whatever the filters")
    void neverReturnsAnotherUsersRows() {
        ActivityLog mine = log(USER, study, LocalDateTime.of(2026, 10, 1, 8, 0), 30, 10);
        log(2L, study, LocalDateTime.of(2026, 10, 1, 9, 0), 30, 10);
        log(2L, study, LocalDateTime.of(2026, 10, 2, 9, 0), 30, 10);

        Page<ActivityLog> p = page(USER, q("0", "100", null, null, null, null));

        assertEquals(List.of(mine.getId()), ids(p));
        assertEquals(1, p.getTotalElements());
        assertEquals(2, page(2L, q(null, null, null, null, null, null)).getTotalElements());
    }

    @Test
    @DisplayName("a user with no logs gets an empty first page, not an error")
    void emptyUser() {
        Page<ActivityLog> p = page(USER, q(null, null, null, null, null, null));

        assertTrue(p.getContent().isEmpty());
        assertEquals(0, p.getTotalElements());
        assertEquals(0, p.getTotalPages());
    }
}
