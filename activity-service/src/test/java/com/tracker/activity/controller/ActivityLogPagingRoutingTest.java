package com.tracker.activity.controller;

import com.tracker.activity.domain.ActivityLogQuery;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.PagedResponse;
import com.tracker.activity.exception.OwnershipViolationException;
import com.tracker.activity.service.ActivityLogService;
import com.tracker.activity.service.NaturalLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The unpaged and paged reads share one URL and are told apart by Spring's {@code params} condition.
 * That is what guarantees "unpaged keeps working" -- so it is pinned through real routing, not by
 * calling the controller methods directly.
 */
@WebMvcTest(ActivityLogController.class)
@DisplayName("GET /activitylog/user/{id}: unpaged vs paged routing")
class ActivityLogPagingRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityLogService activityLogService;

    @MockBean
    private NaturalLogService naturalLogService;

    private static PagedResponse<ActivityLogResponse> emptyPage() {
        return new PagedResponse<>(List.of(), 0, 20, 0, 0, "startTime,desc");
    }

    @Test
    @DisplayName("no new parameter -> the legacy handler, answering a bare JSON array, exactly as before")
    void noParametersIsTheLegacyUnpagedRead() throws Exception {
        when(activityLogService.getAllActivityForUser(7L, 7L)).thenReturn(ResponseEntity.ok(List.of()));

        mockMvc.perform(get("/activitylog/user/7").header("userId", 7L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(activityLogService).getAllActivityForUser(7L, 7L);
        verify(activityLogService, never()).getActivityForUserPaged(any(), any(), any());
    }

    @Test
    @DisplayName("an unrelated query parameter does not switch to paging")
    void unrelatedParametersStayLegacy() throws Exception {
        when(activityLogService.getAllActivityForUser(7L, 7L)).thenReturn(ResponseEntity.ok(List.of()));

        mockMvc.perform(get("/activitylog/user/7").param("cacheBuster", "123").header("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(activityLogService, never()).getActivityForUserPaged(any(), any(), any());
    }

    @Test
    @DisplayName("ANY ONE of page, size, sort, activityId, from, to selects the paged handler")
    void anySingleNewParameterSelectsPaging() throws Exception {
        when(activityLogService.getActivityForUserPaged(eq(7L), eq(7L), any(ActivityLogQuery.class)))
                .thenReturn(ResponseEntity.ok(emptyPage()));

        Map<String, String> oneEach = Map.of(
                "page", "0", "size", "10", "sort", "xpEarned,desc",
                "activityId", "3", "from", "2026-10-01", "to", "2026-10-31");
        for (var param : oneEach.entrySet()) {
            mockMvc.perform(get("/activitylog/user/7").param(param.getKey(), param.getValue()).header("userId", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        verify(activityLogService, never()).getAllActivityForUser(any(), any());
    }

    @Test
    @DisplayName("the paged response is an envelope with every key a pager needs")
    void pagedEnvelopeShape() throws Exception {
        when(activityLogService.getActivityForUserPaged(eq(7L), eq(7L), any(ActivityLogQuery.class)))
                .thenReturn(ResponseEntity.ok(new PagedResponse<>(List.of(), 2, 10, 45, 5, "xpEarned,desc")));

        mockMvc.perform(get("/activitylog/user/7").param("page", "2").param("size", "10").header("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(45))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.sort").value("xpEarned,desc"))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("the parsed query reaches the service with the values the client sent")
    void parametersAreParsedIntoTheQuery() throws Exception {
        when(activityLogService.getActivityForUserPaged(eq(7L), eq(7L), any(ActivityLogQuery.class)))
                .thenReturn(ResponseEntity.ok(emptyPage()));

        mockMvc.perform(get("/activitylog/user/7")
                        .param("page", "3").param("size", "25").param("sort", "durationMinutes,asc")
                        .param("activityId", "9").param("from", "2026-10-01").param("to", "2026-10-31")
                        .header("userId", 7L))
                .andExpect(status().isOk());

        verify(activityLogService).getActivityForUserPaged(eq(7L), eq(7L), eq(
                ActivityLogQuery.parse("3", "25", "durationMinutes,asc", "9", "2026-10-01", "2026-10-31")));
    }

    @Test
    @DisplayName("a bad value is a 400 ProblemDetail naming the parameter, and nothing is queried")
    void invalidValuesAre400() throws Exception {
        record Bad(String name, String value, String expectedDetail) {
        }
        for (Bad bad : List.of(
                new Bad("size", "0", "size must be between 1 and 100 (got 0)"),
                new Bad("size", "1000", "size must be between 1 and 100 (got 1000)"),
                new Bad("page", "-1", "page must be >= 0 (got -1)"),
                new Bad("page", "x", "page must be a whole number (got 'x')"),
                new Bad("sort", "password,asc",
                        "sort field must be one of [createdAt, durationMinutes, endTime, startTime, xpEarned] (got 'password')"),
                new Bad("activityId", "abc", "activityId must be a number (got 'abc')"),
                new Bad("from", "yesterday", "from must be an ISO date like 2026-10-01 (got 'yesterday')"))) {
            mockMvc.perform(get("/activitylog/user/7").param(bad.name(), bad.value()).header("userId", 7L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value(bad.expectedDetail()));
        }

        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("the paged read keeps the ownership rule: someone else's logs are a 403")
    void pagedReadIsOwnerOnly() throws Exception {
        when(activityLogService.getActivityForUserPaged(eq(1L), eq(2L), any(ActivityLogQuery.class)))
                .thenThrow(new OwnershipViolationException("Not permitted to access another user's data"));

        mockMvc.perform(get("/activitylog/user/2").param("page", "0").header("userId", 1L))
                .andExpect(status().isForbidden());
    }
}
