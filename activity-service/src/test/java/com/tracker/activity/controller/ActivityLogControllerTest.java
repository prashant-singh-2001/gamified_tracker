package com.tracker.activity.controller;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.service.ActivityLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Log Controller Tests")
public class ActivityLogControllerTest {

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private ActivityLogController activityLogController;

    @Test
    @DisplayName("Test getActivityLog method")
    void testGetActivityLog() {
        //Arrange
        Long id = 1L;
        LocalDateTime now = LocalDateTime.now();
        Activity activity = Activity.builder()
                .id(id)
                .name("Running")
                .category(Category.HEALTH)
                .xpMultiplier(1.5)
                .active(true)
                .description("Running activity")
                .createdAt(now)
                .build();

        ActivityLogResponse response = new ActivityLogResponse(
                id,
                2L,
                activity,
                now,
                now.plusMinutes(30),
                30L,
                50.0,
                "Good run",
                now,
                false,
                1.0,
                false,
                0,
                1.0
        );

        //Act
        ResponseEntity<ActivityLogResponse> expectedResponse = ResponseEntity.ok(response);
        when(activityLogService.getActivityLogResponseEntity(id)).thenReturn(expectedResponse);

        //Assert
        ResponseEntity<ActivityLogResponse> actualResponse = activityLogController.getActivityLog(id);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    @DisplayName("Test addActivityLog method")
    void testAddActivityLog() {
        //Arrange
        LocalDateTime now = LocalDateTime.now();
        Long userId = 2L;
        // IDOR fix: userId no longer lives on the request body; it comes from the
        // trusted "userId" header instead.
        // ActivityLogRequest request = new ActivityLogRequest(
        //         2L,
        //         "Running",
        //         now,
        //         now.plusMinutes(30),
        //         "Morning run",
        //         now
        // );
        ActivityLogRequest request = new ActivityLogRequest(
                "Running",
                now,
                now.plusMinutes(30),
                "Morning run",
                now
        );

        Activity activity = Activity.builder()
                .id(1L)
                .name("Running")
                .category(Category.HEALTH)
                .xpMultiplier(1.5)
                .active(true)
                .description("Running activity")
                .createdAt(now)
                .build();

        ActivityLogResponse response = new ActivityLogResponse(
                1L,
                2L,
                activity,
                now,
                now.plusMinutes(30),
                30L,
                50.0,
                "Morning run",
                now,
                false,
                1.0,
                false,
                0,
                1.0
        );

        //Act
        ResponseEntity<ActivityLogResponse> expectedResult = ResponseEntity.ok(response);
        // when(activityLogService.addActivityLogResponseResponseEntity(request)).thenReturn(expectedResult);
        when(activityLogService.addActivityLogResponseResponseEntity(userId, request)).thenReturn(expectedResult);

        //Assert
        // ResponseEntity<ActivityLogResponse> actualResult = activityLogController.addActivityLog(request);
        ResponseEntity<ActivityLogResponse> actualResult = activityLogController.addActivityLog(userId, request);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    @DisplayName("Test getAllActivityForUser method")
    void testGetAllActivityForUser() {
        //Arrange
        Long userId = 2L;
        LocalDateTime now = LocalDateTime.now();

        Activity activity1 = Activity.builder()
                .id(1L)
                .name("Running")
                .category(Category.HEALTH)
                .xpMultiplier(1.5)
                .active(true)
                .description("Running activity")
                .createdAt(now)
                .build();

        Activity activity2 = Activity.builder()
                .id(2L)
                .name("Swimming")
                .category(Category.HEALTH)
                .xpMultiplier(2.0)
                .active(true)
                .description("Swimming activity")
                .createdAt(now)
                .build();

        Activity activity3 = Activity.builder()
                .id(3L)
                .name("Cycling")
                .category(Category.HEALTH)
                .xpMultiplier(1.8)
                .active(true)
                .description("Cycling activity")
                .createdAt(now)
                .build();

        ActivityLogResponse log1 = new ActivityLogResponse(
                1L,
                userId,
                activity1,
                now,
                now.plusMinutes(30),
                30L,
                50.0,
                "Morning run",
                now,
                false,
                1.0,
                false,
                0,
                1.0
        );
        ActivityLogResponse log2 = new ActivityLogResponse(
                2L,
                userId,
                activity2,
                now.plusHours(1),
                now.plusHours(2),
                60L,
                80.0,
                "Swimming session",
                now.plusHours(1),
                false,
                1.0,
                false,
                0,
                1.0
        );
        ActivityLogResponse log3 = new ActivityLogResponse(
                3L,
                userId,
                activity3,
                now.plusHours(3),
                now.plusHours(4),
                60L,
                70.0,
                "Evening cycle",
                now.plusHours(3),
                false,
                1.0,
                false,
                0,
                1.0
        );

        //Act
        ResponseEntity<List<ActivityLogResponse>> expectedResponse = ResponseEntity.ok(List.of(log1, log2, log3));
        when(activityLogService.getAllActivityForUser(userId)).thenReturn(expectedResponse);

        //Assert
        ResponseEntity<List<ActivityLogResponse>> actualResponse = activityLogController.getAllActivityForUser(userId);
        assertEquals(expectedResponse, actualResponse);
        assertNotNull(actualResponse.getBody());
        assertEquals(3, actualResponse.getBody().size());
    }
}
