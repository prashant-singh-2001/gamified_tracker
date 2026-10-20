package com.tracker.activity.controller;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
public class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityService activityService;

    @Test
    void testGetActivity() throws Exception {
        var response = new ActivityResponseRecord(
                5L,
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
    void testAddActivityInvalidBodyReturnsPerFieldErrors() throws Exception {
        // name blank, category missing, multiplier not positive: three different fields fail at once
        String json = "{\"name\":\"\",\"xpMultiplier\":-1,\"active\":true}";

        mockMvc.perform(post("/activity/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").value("name feild is required"))
                .andExpect(jsonPath("$.errors.category").value("fill up the category"))
                .andExpect(jsonPath("$.errors.xpMultiplier").value("xpMultiplayer cannot be negative or zero"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                // detail stays the joined messages (sorted by field), so a client showing only
                // "detail" is unaffected
                .andExpect(jsonPath("$.detail").value(
                        "fill up the category; name feild is required; xpMultiplayer cannot be negative or zero"));
    }

    @Test
    void testGetAllActivities() throws Exception {
        var response = new ActivityResponseRecord(
                5L,
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
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("Study"));
    }

    private static ActivityResponseRecord study(long id) {
        return new ActivityResponseRecord(id, "Study", Category.STUDY, 1.5, true, "desc", LocalDateTime.now());
    }

    @Test
    void testGetActivityByNumericIdGoesToTheIdHandler_andReturnsTheId() throws Exception {
        when(activityService.getActivityByIdOrName("5")).thenReturn(ResponseEntity.ok(study(5)));

        mockMvc.perform(get("/activity/5").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Study"));

        verify(activityService, never()).getActivity(anyString());
    }

    @Test
    void testNameLookupsKeepGoingToTheNameHandler() throws Exception {
        when(activityService.getActivity("Study")).thenReturn(ResponseEntity.ok(study(5)));
        when(activityService.getActivity("5K")).thenReturn(ResponseEntity.ok(study(6)));

        mockMvc.perform(get("/activity/Study")).andExpect(status().isOk());
        // Not digits-only, so it is a name and must not be treated as an id
        mockMvc.perform(get("/activity/5K")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(6));

        verify(activityService, never()).getActivityByIdOrName(anyString());
    }

    @Test
    void testADigitsOnlyNameLikeTheGameStillResolves_viaTheIdOrNameHandler() throws Exception {
        // "2048" is a legitimate activity NAME. The regex mapping shadows the name route for it,
        // so it must go through the handler that falls back to the name.
        when(activityService.getActivityByIdOrName("2048")).thenReturn(ResponseEntity.ok(study(12)));

        mockMvc.perform(get("/activity/2048"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    void testGetActivityByIdNotFoundIs404ProblemDetail() throws Exception {
        when(activityService.getActivityByIdOrName("99"))
                .thenThrow(new ActivityNotFoundException("Activity not found: 99"));

        mockMvc.perform(get("/activity/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Activity not found: 99"));
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
                9L,
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
                .andExpect(status().isOk())
                // the new activity's id comes back, so a client can use it straight away
                .andExpect(jsonPath("$.id").value(9));

        org.mockito.Mockito.verify(activityService).addActivityEntity(org.mockito.ArgumentMatchers.any(com.tracker.activity.dto.ActivityRequestRecord.class));
    }

    // ---- item 11: catalog administration ----------------------------------------------------------

    @Test
    void patchReturnsTheUpdatedActivity() throws Exception {
        when(activityService.updateActivity(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ResponseEntity.ok(study(5)));

        mockMvc.perform(patch("/activity/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xpMultiplier\":2.0,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void patchRejectsInvalidFieldsWithTheViolationMessages() throws Exception {
        mockMvc.perform(patch("/activity/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"xpMultiplier\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("name must not be blank")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("xpMultiplier cannot be negative")));
    }

    @Test
    void patchWithUnknownCategoryIsABadRequest() throws Exception {
        mockMvc.perform(patch("/activity/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchMapsTheServiceExceptionsToProblemDetails() throws Exception {
        when(activityService.updateActivity(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.tracker.activity.exception.ActivityNameTakenException("Swim"));
        mockMvc.perform(patch("/activity/5").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Swim\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An activity named 'Swim' already exists"));

        when(activityService.updateActivity(org.mockito.ArgumentMatchers.eq(6L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.tracker.activity.exception.InvalidActivityUpdateException("Nothing to update"));
        mockMvc.perform(patch("/activity/6").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        when(activityService.updateActivity(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ActivityNotFoundException("Activity not found: 7"));
        mockMvc.perform(patch("/activity/7").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsTheOutcome() throws Exception {
        when(activityService.deleteActivity(5L)).thenReturn(ResponseEntity.ok(
                new com.tracker.activity.dto.ActivityDeleteResponse(5L, "Study",
                        com.tracker.activity.dto.ActivityDeleteResponse.Result.DEACTIVATED)));

        mockMvc.perform(delete("/activity/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.result").value("DEACTIVATED"));
    }

    @Test
    void includeInactiveSwitchesToTheUnfilteredReads_andItsAbsenceDoesNot() throws Exception {
        when(activityService.getAllActivities(true)).thenReturn(ResponseEntity.ok(List.of(study(5))));
        when(activityService.getActivityByIdOrName("5", true)).thenReturn(ResponseEntity.ok(study(5)));
        when(activityService.getActivity("Study", true)).thenReturn(ResponseEntity.ok(study(5)));

        mockMvc.perform(get("/activity/").param("includeInactive", "true")).andExpect(status().isOk());
        mockMvc.perform(get("/activity/5").param("includeInactive", "true")).andExpect(status().isOk());
        mockMvc.perform(get("/activity/Study").param("includeInactive", "true")).andExpect(status().isOk());

        verify(activityService, never()).getAllActivities();
        verify(activityService, never()).getActivityByIdOrName("5");
        verify(activityService, never()).getActivity("Study");
    }
}
