package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LevelUpEventDto;
import com.tracker.gamification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@DisplayName("Notification Controller Tests")
public class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @DisplayName("GET /notifications returns the caller's events (userId from the trusted header)")
    void getNotifications_returnsCallersEvents() throws Exception {
        var event = new LevelUpEventDto(
                10L,
                1L,
                1,
                2,
                300.0,
                100.0,
                false,
                LocalDateTime.now()
        );

        when(notificationService.getForUser(1L, false)).thenReturn(List.of(event));

        mockMvc.perform(get("/notifications")
                        .header("userId", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].activityId").value(1L))
                .andExpect(jsonPath("$[0].oldLevel").value(1))
                .andExpect(jsonPath("$[0].newLevel").value(2))
                .andExpect(jsonPath("$[0].read").value(false));

        verify(notificationService).getForUser(1L, false);
    }

    @Test
    @DisplayName("GET /notifications?unreadOnly=true forwards the flag to the service")
    void getNotifications_unreadOnly_forwardsFlag() throws Exception {
        when(notificationService.getForUser(1L, true)).thenReturn(List.of());

        mockMvc.perform(get("/notifications")
                        .header("userId", 1L)
                        .param("unreadOnly", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(notificationService).getForUser(1L, true);
    }

    @Test
    @DisplayName("GET /notifications/unread-count returns the caller's unread count")
    void unreadCount_returnsCount() throws Exception {
        when(notificationService.unreadCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/notifications/unread-count")
                        .header("userId", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Count").value(3));

        verify(notificationService).unreadCount(1L);
    }

    @Test
    @DisplayName("POST /notifications/{id}/read marks the event read and returns 204")
    void markRead_returnsNoContent() throws Exception {
        mockMvc.perform(post("/notifications/5/read").header("userId", 1L))
                .andExpect(status().isNoContent());

        verify(notificationService).markRead(1L, 5L);
    }

    @Test
    @DisplayName("POST /notifications/{id}/read returns 404 when the event is missing or not owned")
    void markRead_missingOrNotOwned_returns404() throws Exception {
        doThrow(new NoSuchElementException("not found"))
                .when(notificationService).markRead(eq(1L), eq(99L));

        mockMvc.perform(post("/notifications/99/read").header("userId", 1L))
                .andExpect(status().isNotFound());
    }
}
