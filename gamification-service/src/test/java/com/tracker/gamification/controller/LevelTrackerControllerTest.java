package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.dto.ManualXpAwardRequest;
import com.tracker.gamification.service.impl.LevelTrackerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LevelTrackerController.class)
@DisplayName("Level Tracker Controller Tests")
public class LevelTrackerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LevelTrackerServiceImpl levelTrackerService;

    @Test
    @DisplayName("Test getAllLevelTracker method")
    void testGetAllLevelTracker() throws Exception {
        // OLD: var response1 = new LevelTrackerDto(1L, 1L, 5, 500.0, 250.0, false);
        var response1 = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                250.0,
                50.0,
                false
        );

        // OLD: var response2 = new LevelTrackerDto(2L, 2L, 3, 300.0, 150.0, false);
        var response2 = new LevelTrackerDto(
                2L,
                2L,
                3,
                300.0,
                150.0,
                150.0,
                50.0,
                false
        );

        when(levelTrackerService.findAll())
                .thenReturn(List.of(response1, response2));

        mockMvc.perform(get("/level").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1L))
                .andExpect(jsonPath("$[0].activityId").value(1L))
                .andExpect(jsonPath("$[0].level").value(5))
                .andExpect(jsonPath("$[1].userId").value(2L))
                .andExpect(jsonPath("$[1].level").value(3));
    }

    @Test
    @DisplayName("Test getLevelTrackerById method")
    void testGetLevelTrackerById() throws Exception {
        // OLD: var response = new LevelTrackerDto(1L, 1L, 5, 500.0, 250.0, false);
        var response = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                250.0,
                50.0,
                false
        );

        when(levelTrackerService.findById(anyLong()))
                .thenReturn(response);

        mockMvc.perform(get("/level/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.activityId").value(1L))
                .andExpect(jsonPath("$.level").value(5))
                .andExpect(jsonPath("$.totalXp").value(500.0))
                .andExpect(jsonPath("$.xpForNextLevel").value(250.0))
                .andExpect(jsonPath("$.progressPercent").value(50.0));

        verify(levelTrackerService).findById(anyLong());
    }

    @Test
    @DisplayName("Test awardXpManually method")
    void testAwardXpManually() throws Exception {
        // OLD (pre-#74, unguarded direct save()):
        // when(levelTrackerService.save(anyLong(), any(LevelTrackerRequestDTO.class)))
        //         .thenReturn(response);
        // mockMvc.perform(post("/level")...);
        // verify(levelTrackerService).save(eq(1L), any(LevelTrackerRequestDTO.class));
        var response = new LevelTrackerDto(
                1L,
                1L,
                1,
                100.0,
                100.0,
                100.0,
                50.0,
                false
        );

        when(levelTrackerService.awardManually(anyLong(), any(ManualXpAwardRequest.class)))
                .thenReturn(response);

        String json = "{\"activityId\":1,\"xp\":100.0}";

        mockMvc.perform(post("/level")
                        .header("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.activityId").value(1L))
                .andExpect(jsonPath("$.totalXp").value(100.0));

        verify(levelTrackerService).awardManually(eq(1L), any(ManualXpAwardRequest.class));
    }

    @Test
    @DisplayName("Test awardXpManually routes an explicit targetUserId to the service, separate from the acting admin")
    void testAwardXpManually_withExplicitTarget() throws Exception {
        var response = new LevelTrackerDto(2L, 1L, 1, 50.0, 50.0, 450.0, 10.0, false);

        when(levelTrackerService.awardManually(anyLong(), any(ManualXpAwardRequest.class)))
                .thenReturn(response);

        String json = "{\"targetUserId\":2,\"activityId\":1,\"xp\":50.0}";

        mockMvc.perform(post("/level")
                        .header("userId", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2L));

        verify(levelTrackerService).awardManually(eq(10L), argThat(req ->
                req.targetUserId().equals(2L) && req.activityId().equals(1L) && req.xp() == 50.0));
    }

    @Test
    @DisplayName("Test awardXpManually rejects xp above the per-award cap")
    void testAwardXpManually_rejectsXpAboveCap() throws Exception {
        String json = "{\"activityId\":1,\"xp\":1.0E18}";

        mockMvc.perform(post("/level")
                        .header("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("xp exceeds the per-award cap of 10000"));

        verify(levelTrackerService, never()).awardManually(anyLong(), any());
    }

    @Test
    @DisplayName("Test getLevelTrackerByUserId method")
    void testGetLevelTrackerByUserId() throws Exception {
        // OLD: var response1 = new LevelTrackerDto(1L, 1L, 5, 500.0, 250.0, false);
        var response1 = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                250.0,
                50.0,
                false
        );

        // OLD: var response2 = new LevelTrackerDto(1L, 2L, 3, 300.0, 150.0, false);
        var response2 = new LevelTrackerDto(
                1L,
                2L,
                3,
                300.0,
                150.0,
                150.0,
                50.0,
                false
        );

        when(levelTrackerService.findByUserId(anyLong()))
                .thenReturn(List.of(response1, response2));

        mockMvc.perform(get("/level/user/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1L))
                .andExpect(jsonPath("$[0].activityId").value(1L))
                .andExpect(jsonPath("$[1].userId").value(1L))
                .andExpect(jsonPath("$[1].activityId").value(2L));

        verify(levelTrackerService).findByUserId(anyLong());
    }

    @Test
    @DisplayName("Test getLevelTrackerByActivityId method")
    void testGetLevelTrackerByActivityId() throws Exception {
        // OLD: var response1 = new LevelTrackerDto(1L, 1L, 5, 500.0, 250.0, false);
        var response1 = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                250.0,
                50.0,
                false
        );

        // OLD: var response2 = new LevelTrackerDto(2L, 1L, 3, 300.0, 150.0, false);
        var response2 = new LevelTrackerDto(
                2L,
                1L,
                3,
                300.0,
                150.0,
                150.0,
                50.0,
                false
        );

        when(levelTrackerService.findByActivityId(anyLong()))
                .thenReturn(List.of(response1, response2));

        mockMvc.perform(get("/level/activity/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1L))
                .andExpect(jsonPath("$[0].activityId").value(1L))
                .andExpect(jsonPath("$[1].userId").value(2L))
                .andExpect(jsonPath("$[1].activityId").value(1L));

        verify(levelTrackerService).findByActivityId(anyLong());
    }
}
