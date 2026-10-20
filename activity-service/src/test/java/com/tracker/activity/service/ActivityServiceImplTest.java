package com.tracker.activity.service;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Service Tests")
public class ActivityServiceImplTest {

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityServiceImpl activityService;

    @Test
    @DisplayName("getActivity returns mapped ActivityResponseRecord")
    void testGetActivity() {
        String name = "Running";
        LocalDateTime now = LocalDateTime.now();
        Activity activity = Activity.builder()
                .id(1L)
                .name(name)
                .category(Category.HEALTH)
                .xpMultiplier(1.5)
                .active(true)
                .description("Running activity")
                .createdAt(now)
                .build();

        // #84: getActivity must go through the active-filtered lookup, not the shared findByName
        // that other consumers (log-time guard, fuzzy resolution) deliberately keep unfiltered.
        when(activityRepository.findByNameAndActiveTrue(name)).thenReturn(Optional.of(activity));

        ResponseEntity<ActivityResponseRecord> resp = activityService.getActivity(name);

        assertNotNull(resp);
        assertEquals(name, resp.getBody().name());
        assertEquals(Category.HEALTH, resp.getBody().category());
        verify(activityRepository).findByNameAndActiveTrue(name);
    }

    @Test
    @DisplayName("getActivity throws ActivityNotFoundException when missing OR soft-deleted "
            + "(#84) -- the two are indistinguishable at this layer by design; the real filtering "
            + "proof is in ActivityRepositoryTest")
    void testGetActivityNotFound() {
        String name = "Unknown";
        when(activityRepository.findByNameAndActiveTrue(name)).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class, () -> activityService.getActivity(name));
        verify(activityRepository).findByNameAndActiveTrue(name);
    }

    private static Activity activityWith(Long id, String name) {
        return Activity.builder().id(id).name(name).category(Category.OTHER).xpMultiplier(1.0)
                .active(true).description("d").createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("responses carry the activity's id")
    void testResponsesCarryTheId() {
        when(activityRepository.findByNameAndActiveTrue("Running")).thenReturn(Optional.of(activityWith(42L, "Running")));

        assertEquals(42L, activityService.getActivity("Running").getBody().id());
    }

    @Test
    @DisplayName("getActivityByIdOrName: a digits-only segment is tried as an id first")
    void testGetByIdFound() {
        when(activityRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(activityWith(7L, "Study")));

        var resp = activityService.getActivityByIdOrName("7");

        assertEquals(7L, resp.getBody().id());
        assertEquals("Study", resp.getBody().name());
        // found by id, so the name lookup must not run
        verify(activityRepository, never()).findByNameAndActiveTrue(any());
    }

    @Test
    @DisplayName("getActivityByIdOrName: an inactive or unknown id is a 404 (#84 -- indistinguishable by design)")
    void testGetByIdInactiveOrUnknown() {
        when(activityRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());
        when(activityRepository.findByNameAndActiveTrue("99")).thenReturn(Optional.empty());

        var ex = assertThrows(ActivityNotFoundException.class, () -> activityService.getActivityByIdOrName("99"));

        assertEquals("Activity not found: 99", ex.getMessage());
    }

    @Test
    @DisplayName("getActivityByIdOrName: a digits-only NAME ('2048') still resolves, via the name fallback")
    void testGetByIdFallsBackToADigitsOnlyName() {
        when(activityRepository.findByIdAndActiveTrue(2048L)).thenReturn(Optional.empty());
        when(activityRepository.findByNameAndActiveTrue("2048")).thenReturn(Optional.of(activityWith(3L, "2048")));

        var resp = activityService.getActivityByIdOrName("2048");

        assertEquals("2048", resp.getBody().name());
        assertEquals(3L, resp.getBody().id());
    }

    @Test
    @DisplayName("getActivityByIdOrName: when the digits are one activity's id and another's name, the id wins")
    void testIdBeatsAName() {
        when(activityRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(activityWith(5L, "Chess")));

        var resp = activityService.getActivityByIdOrName("5");

        assertEquals("Chess", resp.getBody().name());
        verify(activityRepository, never()).findByNameAndActiveTrue(any());
    }

    @Test
    @DisplayName("getActivityByIdOrName: digits too long for a Long are not an id -- tried as a name, no exception")
    void testHugeDigitStringIsNotAnIdButMayBeAName() {
        String huge = "99999999999999999999999";
        when(activityRepository.findByNameAndActiveTrue(huge)).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class, () -> activityService.getActivityByIdOrName(huge));

        verify(activityRepository, never()).findByIdAndActiveTrue(any());
    }

    @Test
    @DisplayName("addActivityEntity saves and returns response")
    void testAddActivityEntity() {
        LocalDateTime now = LocalDateTime.now();
        ActivityRequestRecord request = new ActivityRequestRecord(
                "Reading",
                Category.STUDY,
                1.5,
                true,
                "Read books",
                now
        );

        when(activityRepository.save(any())).thenAnswer(invocation -> {
            Activity arg = invocation.getArgument(0);
            arg.setId(10L);
            return arg;
        });

        ResponseEntity<ActivityResponseRecord> resp = activityService.addActivityEntity(request);

        assertNotNull(resp);
        assertEquals(request.name(), resp.getBody().name());
        assertEquals(request.category(), resp.getBody().category());
        verify(activityRepository).save(any());
    }

    @Test
    @DisplayName("getAllActivities returns mapped list, sourced from the active-filtered query (#84)")
    void testGetAllActivities() {
        LocalDateTime now = LocalDateTime.now();
        Activity a1 = Activity.builder().id(1L).name("A").category(Category.OTHER).xpMultiplier(1.0).active(true).createdAt(now).build();
        Activity a2 = Activity.builder().id(2L).name("B").category(Category.WORK).xpMultiplier(1.5).active(true).createdAt(now).build();

        // #84: findAllByActiveTrue, not the shared findAll() -- a soft-deleted activity (which
        // this stub never hands back) must not appear in the catalog listing.
        when(activityRepository.findAllByActiveTrue()).thenReturn(List.of(a1, a2));

        ResponseEntity<List<ActivityResponseRecord>> resp = activityService.getAllActivities();

        assertNotNull(resp);
        assertEquals(2, resp.getBody().size());
        verify(activityRepository).findAllByActiveTrue();
    }

    @Test
    @DisplayName("#84: addActivityEntity defaults a null/omitted active to true, so a client that "
            + "doesn't send the field can't accidentally create an invisible, unrecoverable activity")
    void testAddActivityEntity_omittedActive_defaultsToActive() {
        LocalDateTime now = LocalDateTime.now();
        ActivityRequestRecord request = new ActivityRequestRecord(
                "Reading", Category.STUDY, 1.5, null, "Read books", now);

        when(activityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ActivityResponseRecord> resp = activityService.addActivityEntity(request);

        assertTrue(resp.getBody().active());

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    @DisplayName("#84: addActivityEntity respects an explicit active=false")
    void testAddActivityEntity_explicitFalse_staysInactive() {
        LocalDateTime now = LocalDateTime.now();
        ActivityRequestRecord request = new ActivityRequestRecord(
                "Retired", Category.OTHER, 1.0, false, "no longer offered", now);

        when(activityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ActivityResponseRecord> resp = activityService.addActivityEntity(request);

        assertFalse(resp.getBody().active());
    }
}

