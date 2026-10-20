package com.tracker.gamification.controller;

import com.tracker.gamification.dto.ActivityLevelThresholdDto;
import com.tracker.gamification.service.impl.ActivityLevelThresholdServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityLevelThresholdController.class)
public class ActivityLevelThresholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityLevelThresholdServiceImpl activityLevelThresholdService;

    @Test
    void putUpdatesTheThreshold() throws Exception {
        when(activityLevelThresholdService.updateActivityLevelThreshold(1L, 2, 300.0))
                .thenReturn(new ActivityLevelThresholdDto(1L, 2, 300.0));

        mockMvc.perform(put("/threshold/1/2").contentType(MediaType.APPLICATION_JSON).content("{\"xpRequired\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(1))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.xpRequired").value(300.0));
    }

    @Test
    void putValidatesTheBody() throws Exception {
        mockMvc.perform(put("/threshold/1/2").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("xpRequired is required"));
        mockMvc.perform(put("/threshold/1/2").contentType(MediaType.APPLICATION_JSON).content("{\"xpRequired\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putMapsNonMonotonicTo409AndMissingTo404() throws Exception {
        when(activityLevelThresholdService.updateActivityLevelThreshold(1L, 2, 50.0))
                .thenThrow(new com.tracker.gamification.exception.InvalidThresholdCurveException("Level 2 must need more XP than level 1 (100.0), but got 50.0"));
        when(activityLevelThresholdService.updateActivityLevelThreshold(1L, 9, 50.0))
                .thenThrow(new java.util.NoSuchElementException("ActivityLevelThreshold not found for activity 1 level 9"));

        mockMvc.perform(put("/threshold/1/2").contentType(MediaType.APPLICATION_JSON).content("{\"xpRequired\":50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Level 2 must need more XP than level 1 (100.0), but got 50.0"));
        mockMvc.perform(put("/threshold/1/9").contentType(MediaType.APPLICATION_JSON).content("{\"xpRequired\":50}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204AndMissingIs404() throws Exception {
        mockMvc.perform(delete("/threshold/1/2")).andExpect(status().isNoContent());
        verify(activityLevelThresholdService).deleteActivityLevelThreshold(1L, 2);

        org.mockito.Mockito.doThrow(new java.util.NoSuchElementException("ActivityLevelThreshold not found for activity 1 level 9"))
                .when(activityLevelThresholdService).deleteActivityLevelThreshold(1L, 9);
        mockMvc.perform(delete("/threshold/1/9")).andExpect(status().isNotFound());
    }

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




