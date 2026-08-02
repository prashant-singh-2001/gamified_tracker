package com.tracker.activity.controller;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.DurationOutlierDetector;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.ReviewQueueItemResponse;
import com.tracker.activity.service.ActivityLogReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityLogReviewController.class)
class ActivityLogReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityLogReviewService activityLogReviewService;

    private ActivityLogResponse sampleResponse(Long id, ReviewStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Activity activity = Activity.builder().id(10L).name("Study").category(Category.STUDY)
                .xpMultiplier(1.5).active(true).createdAt(now).build();
        return new ActivityLogResponse(id, 1L, activity, now.minusHours(1), now, 900L, 1350.0,
                "notes", now, false, 1.0, false, 0, 1.0, status);
    }

    @Test
    void getFlaggedLogs_returnsTheQueue() throws Exception {
        var item = new ReviewQueueItemResponse(
                sampleResponse(1L, ReviewStatus.FLAGGED), 9.9, 30.0, 15, DurationOutlierDetector.Basis.MODIFIED_Z_SCORE);
        when(activityLogReviewService.getFlaggedLogs()).thenReturn(ResponseEntity.ok(List.of(item)));

        mockMvc.perform(get("/activitylog/review/flagged").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activityLog.id").value(1))
                .andExpect(jsonPath("$[0].basis").value("MODIFIED_Z_SCORE"));
    }

    @Test
    void approve_returnsTheApprovedLog() throws Exception {
        when(activityLogReviewService.approve(5L)).thenReturn(ResponseEntity.ok(sampleResponse(5L, ReviewStatus.APPROVED)));

        mockMvc.perform(post("/activitylog/review/5/approve").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        verify(activityLogReviewService).approve(5L);
    }

    @Test
    void reject_returnsTheRejectedLog() throws Exception {
        when(activityLogReviewService.reject(6L)).thenReturn(ResponseEntity.ok(sampleResponse(6L, ReviewStatus.REJECTED)));

        mockMvc.perform(post("/activitylog/review/6/reject").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));

        verify(activityLogReviewService).reject(6L);
    }
}
