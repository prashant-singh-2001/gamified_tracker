package com.tracker.gateway.controller;

import com.tracker.gateway.auth.AuthService;
import com.tracker.gateway.auth.JwtUtil;
import com.tracker.gateway.client.ActivityClient;
import com.tracker.gateway.dao.Activity;
import com.tracker.gateway.dao.Category;
import com.tracker.gateway.dto.ActivityLogResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityLogGatewayController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ActivityLogGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityClient activityClient;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void testGetActivityLog() throws Exception {
        var activity = new Activity(1L, "Study", Category.STUDY, 1.5, true, "desc", java.time.LocalDateTime.now());

        var response = new ActivityLogResponse(
                1L,
                100L,
                activity,
                LocalDateTime.now(),
                LocalDateTime.now(),
                60L,
                100.0,
                "notes",
                LocalDateTime.now(),
                false,
                1.0,
                false
        );

        when(activityClient.getActivityLog(1L)).thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(get("/api/activitylog/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testAddActivityLog() throws Exception {
        var activity = new Activity(1L, "Study", Category.STUDY, 1.5, true, "desc", java.time.LocalDateTime.now());

        var response = new ActivityLogResponse(
                1L,
                100L,
                activity,
                LocalDateTime.now(),
                LocalDateTime.now(),
                60L,
                100.0,
                "notes",
                LocalDateTime.now(),
                false,
                1.0,
                false
        );

        when(activityClient.addActivityLog(any())).thenReturn(ResponseEntity.ok(response));

        String json = "{\"userId\":100,\"activityName\":\"Study\",\"startTime\":\"2025-01-01T10:00:00\",\"endTime\":\"2025-01-01T11:00:00\",\"notes\":\"notes\"}";

        mockMvc.perform(post("/api/activitylog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void testGetAllActivityForUser() throws Exception {
        var activity = new Activity(1L, "Study", Category.STUDY, 1.5, true, "desc", java.time.LocalDateTime.now());

        var response = new ActivityLogResponse(
                1L,
                100L,
                activity,
                LocalDateTime.now(),
                LocalDateTime.now(),
                60L,
                100.0,
                "notes",
                LocalDateTime.now(),
                false,
                1.0,
                false
        );

        when(activityClient.getAllActivityForUser(100L)).thenReturn(ResponseEntity.ok(List.of(response)));

        mockMvc.perform(get("/api/activitylog/user/100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}



