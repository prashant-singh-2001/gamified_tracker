package com.tracker.activity.dto;

import java.util.List;

/**
 * One page of results. A deliberate, minimal envelope rather than Spring's {@code Page} (whose JSON
 * is large and not a stable contract): the total is what a client needs to draw a pager.
 *
 * @param content       this page's rows, already in the requested order
 * @param page          zero-based page index actually served
 * @param size          the page size actually used (the default or the requested one)
 * @param totalElements rows matching the filters across all pages
 * @param totalPages    {@code ceil(totalElements / size)}; 0 when nothing matches
 * @param sort          the sort actually applied, e.g. {@code "startTime,desc"}
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, String sort) {
}
