package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.service.impl.LevelTrackerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        var response1 = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                false
        );

        var response2 = new LevelTrackerDto(
                2L,
                2L,
                3,
                300.0,
                150.0,
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
        var response = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                false
        );

        when(levelTrackerService.findById(anyLong()))
                .thenReturn(response);

        mockMvc.perform(get("/level/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.activityId").value(1L))
                .andExpect(jsonPath("$.level").value(5))
                .andExpect(jsonPath("$.totalXp").value(500.0));

        verify(levelTrackerService).findById(anyLong());
    }

    @Test
    @DisplayName("Test createLevelTracker method")
    void testCreateLevelTracker() throws Exception {
        var response = new LevelTrackerDto(
                1L,
                1L,
                1,
                100.0,
                100.0,
                false
        );

        when(levelTrackerService.save(any(LevelTrackerRequestDTO.class)))
                .thenReturn(response);

        String json = "{\"userId\":1,\"activityId\":1,\"xp\":100.0}";

        mockMvc.perform(post("/level")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.activityId").value(1L))
                .andExpect(jsonPath("$.totalXp").value(100.0));

        verify(levelTrackerService).save(any(LevelTrackerRequestDTO.class));
    }

    @Test
    @DisplayName("Test getLevelTrackerByUserId method")
    void testGetLevelTrackerByUserId() throws Exception {
        var response1 = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                false
        );

        var response2 = new LevelTrackerDto(
                1L,
                2L,
                3,
                300.0,
                150.0,
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
        var response1 = new LevelTrackerDto(
                1L,
                1L,
                5,
                500.0,
                250.0,
                false
        );

        var response2 = new LevelTrackerDto(
                2L,
                1L,
                3,
                300.0,
                150.0,
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



