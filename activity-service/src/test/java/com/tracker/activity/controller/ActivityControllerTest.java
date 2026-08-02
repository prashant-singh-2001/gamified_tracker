package com.tracker.activity.controller;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
public class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    @Test
    void testGetActivity() throws Exception {
        var response = new ActivityResponseRecord(
                "Study",
                Category.STUDY,
                1.5,
                true,
                "desc",
                LocalDateTime.now()
        );

        when(activityService.getActivity("Study")).thenReturn(org.springframework.http.ResponseEntity.ok(response));

        mockMvc.perform(get("/activity/Study").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Study"))
                .andExpect(jsonPath("$.category").value("STUDY"));
    }

    @Test
    void testGetAllActivities() throws Exception {
        var response = new ActivityResponseRecord(
                "Study",
                Category.STUDY,
                1.5,
                true,
                "desc",
                LocalDateTime.now()
        );

        when(activityService.getAllActivities()).thenReturn(org.springframework.http.ResponseEntity.ok(List.of(response)));

        mockMvc.perform(get("/activity/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Study"));
    }

    @Test
    void testAddActivity() throws Exception {
        var request = new ActivityRequestRecord(
                "Play",
                Category.GAMING,
                0.8,
                true,
                "fun",
                LocalDateTime.now()
        );

        var response = new ActivityResponseRecord(
                request.name(),
                request.category(),
                request.xpMultiplier(),
                request.active(),
                request.description(),
                request.createdAt()
        );

        when(activityService.addActivityEntity(org.mockito.ArgumentMatchers.any(com.tracker.activity.dto.ActivityRequestRecord.class))).thenReturn(ResponseEntity.ok(response));

        String json = String.format("{\"name\":\"%s\",\"category\":\"%s\",\"xpMultiplier\":%s,\"active\":%s,\"description\":\"%s\"}",
                request.name(), request.category().name(), request.xpMultiplier(), request.active(), request.description());

        mockMvc.perform(post("/activity/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(activityService).addActivityEntity(org.mockito.ArgumentMatchers.any(com.tracker.activity.dto.ActivityRequestRecord.class));
    }
}



