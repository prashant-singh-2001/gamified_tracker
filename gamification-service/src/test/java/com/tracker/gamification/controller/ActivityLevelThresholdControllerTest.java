package com.tracker.gamification.controller;

import com.tracker.gamification.dto.ActivityLevelThresholdDto;
import com.tracker.gamification.service.impl.ActivityLevelThresholdServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityLevelThresholdController.class)
public class ActivityLevelThresholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityLevelThresholdServiceImpl activityLevelThresholdService;

    @Test
    void testGetActivityLevelThreshold() throws Exception {
        var response = new ActivityLevelThresholdDto(
                1L,
                1,
                100.0
        );

        when(activityLevelThresholdService.getAllActivityLevelThreshold())
                .thenReturn(List.of(response));

        mockMvc.perform(get("/threshold").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activityId").value(1L))
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$[0].xpRequired").value(100.0));
    }

    @Test
    void testGetActivityLevelThresholdById() throws Exception {
        var responseDto = new ActivityLevelThresholdDto(
                1L,
                1,
                100.0
        );

        when(activityLevelThresholdService.getActivityLevelThresholdById(any(ActivityLevelThresholdDto.class)))
                .thenReturn(responseDto);

        String json = "{\"activityId\":1,\"level\":1,\"xpRequired\":100.0}";

        mockMvc.perform(post("/threshold/activity")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(1L))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.xpRequired").value(100.0));

        verify(activityLevelThresholdService).getActivityLevelThresholdById(any(ActivityLevelThresholdDto.class));
    }

    @Test
    void testCreateActivityLevelThreshold() throws Exception {
        var response = new ActivityLevelThresholdDto(
                2L,
                2,
                250.0
        );

        when(activityLevelThresholdService.saveActivityLevelThreshold(any(ActivityLevelThresholdDto.class)))
                .thenReturn(response);

        String json = "{\"activityId\":2,\"level\":2,\"xpRequired\":250.0}";

        mockMvc.perform(post("/threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(2L))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.xpRequired").value(250.0));

        verify(activityLevelThresholdService).saveActivityLevelThreshold(any(ActivityLevelThresholdDto.class));
    }
}




