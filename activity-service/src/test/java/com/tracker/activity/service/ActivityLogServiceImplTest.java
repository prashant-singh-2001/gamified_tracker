package com.tracker.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.messaging.ActivityLoggedEvent;
import com.tracker.activity.outbox.OutboxEvent;
import com.tracker.activity.outbox.OutboxEventRepository;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.service.impl.ActivityLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityLog Service Tests")
public class ActivityLogServiceImplTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    // Real ObjectMapper (not mocked) so the outbox payload assertions exercise real
    // serialization/deserialization, matching production behavior.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ActivityLogServiceImpl activityLogService;

    @BeforeEach
    void setUp() {
        activityLogService = new ActivityLogServiceImpl(
                activityLogRepository, activityRepository, outboxEventRepository, objectMapper);
    }

    private void stubActivityAndSave(Activity activity, Long generatedId) {
        when(activityRepository.findByName(activity.getName())).thenReturn(Optional.of(activity));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(invocation -> {
            ActivityLog log = invocation.getArgument(0);
            log.setId(generatedId);
            return log;
        });
    }

    @Test
    @DisplayName("getActivityLogResponseEntity returns mapped response")
    void testGetActivityLogResponseEntity() {
        LocalDateTime now = LocalDateTime.now();
        Activity activity = Activity.builder().id(5L).name("Run").category(Category.HEALTH).xpMultiplier(1.2).active(true).createdAt(now).build();
        ActivityLog log = ActivityLog.builder()
                .id(100L)
                .userId(2L)
                .activity(activity)
                .startTime(now)
                .endTime(now.plusMinutes(20))
                .durationMinutes(20L)
                .xpEarned(24.0)
                .notes("ok")
                .createdAt(now)
                .build();

        when(activityLogRepository.findById(100L)).thenReturn(Optional.of(log));

        ResponseEntity<ActivityLogResponse> resp = activityLogService.getActivityLogResponseEntity(100L);

        assertNotNull(resp);
        assertEquals(100L, resp.getBody().id());
        assertEquals(2L, resp.getBody().userId());
        verify(activityLogRepository).findById(100L);
    }

    @Test
    @DisplayName("getActivityLogResponseEntity throws when missing")
    void testGetActivityLogResponseEntityNotFound() {
        when(activityLogRepository.findById(50L)).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class, () -> activityLogService.getActivityLogResponseEntity(50L));
    }

    @Test
    @DisplayName("addActivityLogResponseResponseEntity saves the log BEFORE writing the outbox row (fixes #4)")
    void addActivityLog_savesLogBeforeOutboxRow() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = 2L;
        ActivityLogRequest request = new ActivityLogRequest("Run", now, now.plusMinutes(30), "nice", now);
        Activity activity = Activity.builder().id(7L).name("Run").category(Category.HEALTH).xpMultiplier(2.0).active(true).createdAt(now).build();
        stubActivityAndSave(activity, 100L);

        activityLogService.addActivityLogResponseResponseEntity(userId, request);

        InOrder inOrder = inOrder(activityLogRepository, outboxEventRepository);
        inOrder.verify(activityLogRepository).save(any(ActivityLog.class));
        inOrder.verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("addActivityLogResponseResponseEntity writes an outbox row carrying the correct ActivityLoggedEvent")
    void addActivityLog_writesCorrectOutboxPayload() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long userId = 2L;
        ActivityLogRequest request = new ActivityLogRequest("Run", now, now.plusMinutes(30), "nice", now);
        Activity activity = Activity.builder().id(7L).name("Run").category(Category.HEALTH).xpMultiplier(2.0).active(true).createdAt(now).build();
        stubActivityAndSave(activity, 100L);

        ResponseEntity<ActivityLogResponse> resp = activityLogService.addActivityLogResponseResponseEntity(userId, request);
        ActivityLogResponse body = resp.getBody();
        assertNotNull(body);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent outboxRow = captor.getValue();

        assertEquals("ActivityLog", outboxRow.getAggregateType());
        assertEquals(100L, outboxRow.getAggregateId());
        assertEquals("ActivityLogged", outboxRow.getEventType());
        assertEquals("100", outboxRow.getIdempotencyKey());
        assertNull(outboxRow.getPublishedAt(), "unpublished until the relay picks it up");
        assertNotNull(outboxRow.getCreatedAt());

        ActivityLoggedEvent deserialized = objectMapper.readValue(outboxRow.getPayload(), ActivityLoggedEvent.class);
        assertEquals(100L, deserialized.logId());
        assertEquals(userId, deserialized.userId());
        assertEquals(activity.getId(), deserialized.activityId());
        assertEquals(body.xpEarned(), deserialized.xpEarned(), 1e-9);
    }

    @Test
    @DisplayName("addActivityLogResponseResponseEntity returns leveledUp=false (eventual) while bonusApplied still reflects the roll")
    void addActivityLog_responseHasEventualLeveledUp() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = 2L;
        ActivityLogRequest request = new ActivityLogRequest("Run", now, now.plusMinutes(30), "nice", now);
        Activity activity = Activity.builder().id(7L).name("Run").category(Category.HEALTH).xpMultiplier(2.0).active(true).createdAt(now).build();
        stubActivityAndSave(activity, 100L);

        ResponseEntity<ActivityLogResponse> resp = activityLogService.addActivityLogResponseResponseEntity(userId, request);
        ActivityLogResponse body = resp.getBody();

        assertNotNull(body);
        assertFalse(body.leveledUp(), "leveledUp is now eventual — XP is applied asynchronously by the consumer");
        // bonus is a 20%-chance random roll, so assert internal consistency rather than a fixed value:
        // bonusApplied must agree with whether the multiplier actually deviated from 1.0.
        assertEquals(body.bonusMultiplier() != 1.0, body.bonusApplied());
    }

    @Test
    @DisplayName("addActivityLogResponseResponseEntity throws when activity not found (no outbox row written)")
    void testAddActivityLogResponseResponseEntityActivityMissing() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Missing", now, now.plusMinutes(30), "notes", now);

        when(activityRepository.findByName("Missing")).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class,
                () -> activityLogService.addActivityLogResponseResponseEntity(2L, request));

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    @DisplayName("getAllActivityForUser returns mapped list")
    void testGetAllActivityForUser() {
        LocalDateTime now = LocalDateTime.now();
        Activity a = Activity.builder().id(1L).name("A").category(Category.OTHER).xpMultiplier(1.0).active(true).createdAt(now).build();

        ActivityLog l1 = ActivityLog.builder().id(1L).userId(2L).activity(a).startTime(now).endTime(now.plusMinutes(10)).durationMinutes(10L).xpEarned(10.0).notes("n").createdAt(now).build();
        ActivityLog l2 = ActivityLog.builder().id(2L).userId(2L).activity(a).startTime(now.plusHours(1)).endTime(now.plusHours(1).plusMinutes(20)).durationMinutes(20L).xpEarned(20.0).notes("n").createdAt(now).build();

        when(activityLogRepository.findByUserId(2L)).thenReturn(List.of(l1, l2));

        ResponseEntity<List<ActivityLogResponse>> resp = activityLogService.getAllActivityForUser(2L);

        assertNotNull(resp);
        assertEquals(2, resp.getBody().size());
        verify(activityLogRepository).findByUserId(2L);
    }
}
