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

