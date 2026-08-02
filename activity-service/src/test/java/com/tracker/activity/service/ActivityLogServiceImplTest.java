package com.tracker.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.config.SessionIntegrityProperties;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.ActivityStreak;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.DurationOutlierDetector;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.exception.ImplausibleSessionException;
import com.tracker.activity.exception.InvalidTimeRangeException;
import com.tracker.activity.outbox.OutboxEvent;
import com.tracker.activity.outbox.OutboxEventRepository;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.repository.ActivityStreakRepository;
import com.tracker.activity.service.DurationOutlierEvaluationService;
import com.tracker.activity.service.impl.ActivityLogServiceImpl;
import com.tracker.contracts.event.ActivityLoggedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

    // Real ObjectMapper (not mocked) so the outbox payload assertions exercise real
    // serialization/deserialization, matching production behavior.
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ActivityStreakRepository activityStreakRepository;
    @Mock
    private DurationOutlierEvaluationService durationOutlierEvaluationService;
    // 1440 min (24h) cap, matching the documented default; outlier detection on but the mocked
    // evaluation service abstains (INSUFFICIENT_SAMPLES) by default -- see below.
    private final SessionIntegrityProperties sessionIntegrityProperties =
            new SessionIntegrityProperties(true, 1440, 3.5, 10, 100, 3.0);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ActivityLogServiceImpl activityLogService;

    @BeforeEach
    void setUp() {
        activityLogService = new ActivityLogServiceImpl(
                activityLogRepository, activityRepository, outboxEventRepository, objectMapper,
                activityStreakRepository, durationOutlierEvaluationService, sessionIntegrityProperties, meterRegistry);
        // Default streak behaviour for the add-log tests that don't care about streaks: save
        // echoes its argument so applyStreak returns a non-null streak. lenient() because the
        // read-only tests never reach this path. findByUserIdAndActivityId returns Optional.empty()
        // via Mockito's default, so those tests see a fresh streak (currentStreak=1, x1.0 -> XP unchanged).
        lenient().when(activityStreakRepository.save(any(ActivityStreak.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // Default verdict for tests that don't care about outlier detection: never flags.
        // lenient() because read-only tests never reach the layer-2 evaluation at all.
        lenient().when(durationOutlierEvaluationService.evaluate(any(), any(), anyLong()))
                .thenReturn(new DurationOutlierDetector.Verdict(false, 0.0, 0.0, 0, DurationOutlierDetector.Basis.INSUFFICIENT_SAMPLES));
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
    @DisplayName("xpEarned uses the Category base multiplier when the activity has no per-activity override (#10)")
    void addActivityLog_usesCategoryDefaultWhenNoOverride() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = 2L;
        ActivityLogRequest request = new ActivityLogRequest("Read", now, now.plusMinutes(30), "nice", now);
        // xpMultiplier 0.0 == "no override" -> falls back to Category.STUDY base (1.5)
        Activity activity = Activity.builder().id(7L).name("Read").category(Category.STUDY).xpMultiplier(0.0).active(true).createdAt(now).build();
        stubActivityAndSave(activity, 100L);

        ActivityLogResponse body = activityLogService.addActivityLogResponseResponseEntity(userId, request).getBody();

        assertNotNull(body);
        // Deterministic despite the random bonus: reconstruct from the returned bonusMultiplier.
        assertEquals(body.durationMinutes() * 1.5 * body.bonusMultiplier(), body.xpEarned(), 1e-9);
    }

    @Test
    @DisplayName("xpEarned uses the per-activity multiplier when one is set, ignoring the Category base (#10)")
    void addActivityLog_usesOverrideWhenPresent() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = 2L;
        ActivityLogRequest request = new ActivityLogRequest("Read", now, now.plusMinutes(30), "nice", now);
        // explicit override 3.0 wins over Category.STUDY base (1.5)
        Activity activity = Activity.builder().id(7L).name("Read").category(Category.STUDY).xpMultiplier(3.0).active(true).createdAt(now).build();
        stubActivityAndSave(activity, 100L);

        ActivityLogResponse body = activityLogService.addActivityLogResponseResponseEntity(userId, request).getBody();

        assertNotNull(body);
        assertEquals(body.durationMinutes() * 3.0 * body.bonusMultiplier(), body.xpEarned(), 1e-9);
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
    @DisplayName("addActivityLogResponseResponseEntity throws InvalidTimeRangeException when endTime is before startTime (no log or outbox row written)")
    void testAddActivityLogResponseResponseEntityEndTimeBeforeStartTime() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Run", now, now.minusMinutes(10), "notes", now);

        InvalidTimeRangeException ex = assertThrows(InvalidTimeRangeException.class,
                () -> activityLogService.addActivityLogResponseResponseEntity(2L, request));

        assertTrue(ex.getMessage().contains("endTime"));
        verifyNoInteractions(activityRepository);
        verifyNoInteractions(activityLogRepository);
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    @DisplayName("addActivityLogResponseResponseEntity throws InvalidTimeRangeException when endTime equals startTime (zero duration is rejected)")
    void testAddActivityLogResponseResponseEntityEndTimeEqualsStartTime() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Run", now, now, "notes", now);

        assertThrows(InvalidTimeRangeException.class,
                () -> activityLogService.addActivityLogResponseResponseEntity(2L, request));

        verifyNoInteractions(activityLogRepository);
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

    @Test
    @DisplayName("first-ever log starts the streak at 1 with a 1.00x multiplier")
    void firstLog_startsStreakAtOne() {
        Activity activity = Activity.builder().id(7L).name("Read").category(Category.STUDY)
                .xpMultiplier(1.0).active(true).build();
        stubActivityAndSave(activity, 100L);
        when(activityStreakRepository.findByUserIdAndActivityId(1L, 7L)).thenReturn(Optional.empty());
        when(activityStreakRepository.save(any(ActivityStreak.class))).thenAnswer(i -> i.getArgument(0));

        var req = new ActivityLogRequest("Read",
                LocalDateTime.now().minusMinutes(30), LocalDateTime.now(), "note", null);
        var body = activityLogService.addActivityLogResponseResponseEntity(1L, req).getBody();

        assertEquals(1, body.currentStreak());
        assertEquals(1.0, body.streakMultiplier());
    }

    @Test
    @DisplayName("a log the day after the last one extends the streak and raises the multiplier")
    void consecutiveDay_extendsStreak() {
        Activity activity = Activity.builder().id(7L).name("Read").category(Category.STUDY)
                .xpMultiplier(1.0).active(true).build();
        stubActivityAndSave(activity, 101L);

        LocalDateTime start = LocalDateTime.now().minusMinutes(30);
        ActivityStreak existing = ActivityStreak.builder()
                .userId(1L).activityId(7L).currentStreak(4).longestStreak(4)
                .lastActivityDate(start.toLocalDate().minusDays(1))   // yesterday
                .build();
        when(activityStreakRepository.findByUserIdAndActivityId(1L, 7L)).thenReturn(Optional.of(existing));
        when(activityStreakRepository.save(any(ActivityStreak.class))).thenAnswer(i -> i.getArgument(0));

        var req = new ActivityLogRequest("Read", start, start.plusMinutes(30), "note", null);
        var body = activityLogService.addActivityLogResponseResponseEntity(1L, req).getBody();

        assertEquals(5, body.currentStreak());
        assertEquals(1.20, body.streakMultiplier(), 1e-9);   // 1.0 + min(4,10)*0.05

        ArgumentCaptor<ActivityStreak> captor = ArgumentCaptor.forClass(ActivityStreak.class);
        verify(activityStreakRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getCurrentStreak());
        assertEquals(5, captor.getValue().getLongestStreak());
    }

    @Test
    @DisplayName("a gap of more than one day resets the streak to 1")
    void missedDay_resetsStreak() {
        Activity activity = Activity.builder().id(7L).name("Read").category(Category.STUDY)
                .xpMultiplier(1.0).active(true).build();
        stubActivityAndSave(activity, 102L);

        LocalDateTime start = LocalDateTime.now().minusMinutes(30);
        ActivityStreak existing = ActivityStreak.builder()
                .userId(1L).activityId(7L).currentStreak(8).longestStreak(8)
                .lastActivityDate(start.toLocalDate().minusDays(3))   // 3 days ago
                .build();
        when(activityStreakRepository.findByUserIdAndActivityId(1L, 7L)).thenReturn(Optional.of(existing));
        when(activityStreakRepository.save(any(ActivityStreak.class))).thenAnswer(i -> i.getArgument(0));

        var req = new ActivityLogRequest("Read", start, start.plusMinutes(30), "note", null);
        var body = activityLogService.addActivityLogResponseResponseEntity(1L, req).getBody();

        assertEquals(1, body.currentStreak());
        assertEquals(1.0, body.streakMultiplier());
        // longestStreak is preserved even though currentStreak reset
        ArgumentCaptor<ActivityStreak> captor = ArgumentCaptor.forClass(ActivityStreak.class);
        verify(activityStreakRepository).save(captor.capture());
        assertEquals(8, captor.getValue().getLongestStreak());
    }

    @Test
    @DisplayName("a second log the same day does not advance the streak")
    void sameDay_doesNotAdvance() {
        Activity activity = Activity.builder().id(7L).name("Read").category(Category.STUDY)
                .xpMultiplier(1.0).active(true).build();
        stubActivityAndSave(activity, 103L);

        LocalDateTime start = LocalDateTime.now().minusMinutes(30);
        ActivityStreak existing = ActivityStreak.builder()
                .userId(1L).activityId(7L).currentStreak(6).longestStreak(6)
                .lastActivityDate(start.toLocalDate())   // already logged today
                .build();
        when(activityStreakRepository.findByUserIdAndActivityId(1L, 7L)).thenReturn(Optional.of(existing));
        when(activityStreakRepository.save(any(ActivityStreak.class))).thenAnswer(i -> i.getArgument(0));

        var req = new ActivityLogRequest("Read", start, start.plusMinutes(30), "note", null);
        var body = activityLogService.addActivityLogResponseResponseEntity(1L, req).getBody();

        assertEquals(6, body.currentStreak());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Issue #7 — Enforce Activity.active soft-delete flag
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addActivityLog throws InactiveActivityException when activity is soft-deleted (no XP/streak/outbox side-effects)")
    void testAddActivityLog_inactiveActivity_isRejected() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Gaming", now, now.plusMinutes(60), "notes", now);

        Activity inactive = Activity.builder()
                .id(99L)
                .name("Gaming")
                .category(Category.GAMING)
                .xpMultiplier(0.8)
                .active(false) // soft-deleted
                .build();

        when(activityRepository.findByName("Gaming")).thenReturn(Optional.of(inactive));

        assertThrows(com.tracker.activity.exception.InactiveActivityException.class,
                () -> activityLogService.addActivityLogResponseResponseEntity(1L, request));

        // nothing must be written when the activity is inactive
        verifyNoInteractions(activityLogRepository);
        verifyNoInteractions(outboxEventRepository);
        verifyNoInteractions(activityStreakRepository);
    }

    @Test
    @DisplayName("addActivityLog succeeds normally when activity.active is true")
    void testAddActivityLog_activeActivity_isAllowed() {
        LocalDateTime start = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(30), "notes", start);

        Activity active = Activity.builder()
                .id(10L)
                .name("Study")
                .category(Category.STUDY)
                .xpMultiplier(1.5)
                .active(true) // enabled
                .build();

        ActivityLog savedLog = ActivityLog.builder()
                .id(1L)
                .userId(1L)
                .activity(active)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .durationMinutes(30L)
                .xpEarned(45.0)
                .notes("notes")
                .createdAt(start)
                .build();

        when(activityRepository.findByName("Study")).thenReturn(Optional.of(active));
        when(activityLogRepository.save(any())).thenReturn(savedLog);
        when(activityStreakRepository.findByUserIdAndActivityId(1L, 10L)).thenReturn(Optional.empty());
        when(activityStreakRepository.save(any(ActivityStreak.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<com.tracker.activity.dto.ActivityLogResponse> response =
                activityLogService.addActivityLogResponseResponseEntity(1L, request);

        assertEquals(200, response.getStatusCode().value());
        verify(activityLogRepository).save(any());
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Issue #67 — Session integrity: hard cap (layer 1) + statistical quarantine (layer 2)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a session over the hard cap is rejected with no log, streak, or outbox row written")
    void addActivityLog_overHardCap_isRejected() {
        LocalDateTime start = LocalDateTime.now();
        // 1441 minutes > the 1440-minute cap configured in this test's SessionIntegrityProperties
        ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(1441), "notes", start);

        Activity active = Activity.builder().id(10L).name("Study").category(Category.STUDY)
                .xpMultiplier(1.5).active(true).build();
        when(activityRepository.findByName("Study")).thenReturn(Optional.of(active));

        assertThrows(ImplausibleSessionException.class,
                () -> activityLogService.addActivityLogResponseResponseEntity(1L, request));

        verifyNoInteractions(activityLogRepository);
        verifyNoInteractions(outboxEventRepository);
        verifyNoInteractions(activityStreakRepository);
    }

    @Test
    @DisplayName("a statistically flagged log is persisted as FLAGGED but writes NO outbox row (XP withheld)")
    void addActivityLog_flaggedByOutlierDetection_withholdsXp() {
        LocalDateTime start = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(60), "notes", start);

        Activity active = Activity.builder().id(10L).name("Study").category(Category.STUDY)
                .xpMultiplier(1.5).active(true).build();
        stubActivityAndSave(active, 200L);

        when(durationOutlierEvaluationService.evaluate(eq(1L), eq(Category.STUDY), eq(60L)))
                .thenReturn(new DurationOutlierDetector.Verdict(true, 9.9, 30.0, 15, DurationOutlierDetector.Basis.MODIFIED_Z_SCORE));

        ActivityLogResponse body = activityLogService.addActivityLogResponseResponseEntity(1L, request).getBody();

        assertNotNull(body);
        assertEquals(ReviewStatus.FLAGGED, body.reviewStatus());
        // the single most important assertion in this change: XP must actually be withheld
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        // the log itself is still persisted (visible to the user, pending review)
        verify(activityLogRepository).save(any(ActivityLog.class));

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertEquals(ReviewStatus.FLAGGED, captor.getValue().getReviewStatus());
    }

    @Test
    @DisplayName("a clean (non-flagged) log is persisted as CLEARED and writes exactly one outbox row")
    void addActivityLog_notFlagged_isClearedAndWritesOutbox() {
        LocalDateTime start = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(30), "notes", start);

        Activity active = Activity.builder().id(10L).name("Study").category(Category.STUDY)
                .xpMultiplier(1.5).active(true).build();
        stubActivityAndSave(active, 201L);
        // default stub from setUp() returns flagged=false

        ActivityLogResponse body = activityLogService.addActivityLogResponseResponseEntity(1L, request).getBody();

        assertNotNull(body);
        assertEquals(ReviewStatus.CLEARED, body.reviewStatus());
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("the outlier-detection kill switch (outlierDetectionEnabled=false) never flags, and the evaluation service is never consulted")
    void addActivityLog_outlierDetectionDisabled_neverFlags() {
        LocalDateTime start = LocalDateTime.now();
        ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(30), "notes", start);

        Activity active = Activity.builder().id(10L).name("Study").category(Category.STUDY)
                .xpMultiplier(1.5).active(true).build();
        stubActivityAndSave(active, 202L);

        SessionIntegrityProperties disabled = new SessionIntegrityProperties(false, 1440, 3.5, 10, 100, 3.0);
        ActivityLogServiceImpl serviceWithDetectionOff = new ActivityLogServiceImpl(
                activityLogRepository, activityRepository, outboxEventRepository, objectMapper,
                activityStreakRepository, durationOutlierEvaluationService, disabled, meterRegistry);

        ActivityLogResponse body = serviceWithDetectionOff.addActivityLogResponseResponseEntity(1L, request).getBody();

        assertNotNull(body);
        assertEquals(ReviewStatus.CLEARED, body.reviewStatus());
        verifyNoInteractions(durationOutlierEvaluationService);
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }
}
