package com.tracker.activity.service;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.ActivityDeleteResponse;
import com.tracker.activity.dto.ActivityRequestRecord;
import com.tracker.activity.dto.ActivityUpdateRequest;
import com.tracker.activity.exception.ActivityNameTakenException;
import com.tracker.activity.exception.InvalidActivityUpdateException;
import org.springframework.dao.DataIntegrityViolationException;
import com.tracker.activity.dto.ActivityResponseRecord;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.repository.ActivityStreakRepository;
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

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private ActivityStreakRepository activityStreakRepository;

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

    // ---- item 11: catalog administration ----------------------------------------------------------

    private static ActivityUpdateRequest patch(String name, Category category, Double xp, String description, Boolean active) {
        return new ActivityUpdateRequest(name, category, xp, description, active);
    }

    @Test
    @DisplayName("addActivityEntity: a name that already exists is a 409, not a constraint 500")
    void testAddDuplicateName() {
        when(activityRepository.findByName("Reading")).thenReturn(Optional.of(activityWith(1L, "Reading")));
        var request = new ActivityRequestRecord("Reading", Category.STUDY, 1.5, true, "d", LocalDateTime.now());

        assertThrows(ActivityNameTakenException.class, () -> activityService.addActivityEntity(request));
        verify(activityRepository, never()).save(any());
    }

    @Test
    @DisplayName("addActivityEntity: losing a create race on the unique constraint is also a 409")
    void testAddRaceOnUniqueConstraint() {
        when(activityRepository.findByName("Reading"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(activityWith(1L, "Reading")));
        when(activityRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk"));
        var request = new ActivityRequestRecord("Reading", Category.STUDY, 1.5, true, "d", LocalDateTime.now());

        assertThrows(ActivityNameTakenException.class, () -> activityService.addActivityEntity(request));
    }

    @Test
    @DisplayName("updateActivity: only the supplied fields change")
    void testPatchChangesOnlySuppliedFields() {
        Activity a = activityWith(3L, "Run");
        a.setCategory(Category.HEALTH);
        a.setDescription("old");
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));
        when(activityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var body = activityService.updateActivity(3L, patch(null, null, 2.0, null, null)).getBody();

        assertEquals(2.0, body.xpMultiplier());
        assertEquals("Run", body.name());
        assertEquals(Category.HEALTH, body.category());
        assertEquals("old", body.description());
        assertTrue(body.active());
    }

    @Test
    @DisplayName("updateActivity: renames (trimmed), changes category and description, and toggles active")
    void testPatchAllFields() {
        Activity a = activityWith(3L, "Run");
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));
        when(activityRepository.findByName("Jog")).thenReturn(Optional.empty());
        when(activityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var body = activityService.updateActivity(3L, patch("  Jog ", Category.HEALTH, 1.2, "new", false)).getBody();

        assertEquals("Jog", body.name());
        assertEquals(Category.HEALTH, body.category());
        assertEquals("new", body.description());
        assertFalse(body.active());
    }

    @Test
    @DisplayName("updateActivity: xpMultiplier 0 clears the override so the category base applies")
    void testPatchZeroClearsOverride() {
        Activity a = activityWith(3L, "Run");
        a.setCategory(Category.WORK);
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));
        when(activityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var body = activityService.updateActivity(3L, patch(null, null, 0.0, null, null)).getBody();

        assertEquals(Category.WORK.baseXpMultiplier(), body.xpMultiplier());
    }

    @Test
    @DisplayName("updateActivity: can re-enable an INACTIVE activity (reads unfiltered)")
    void testPatchReEnables() {
        Activity a = activityWith(3L, "Run");
        a.setActive(false);
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));
        when(activityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(activityService.updateActivity(3L, patch(null, null, null, null, true)).getBody().active());
    }

    @Test
    @DisplayName("updateActivity: renaming to another activity's name is a 409 and saves nothing")
    void testPatchNameTaken() {
        when(activityRepository.findById(3L)).thenReturn(Optional.of(activityWith(3L, "Run")));
        when(activityRepository.findByName("Swim")).thenReturn(Optional.of(activityWith(4L, "Swim")));

        assertThrows(ActivityNameTakenException.class,
                () -> activityService.updateActivity(3L, patch("Swim", null, null, null, null)));
        verify(activityRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("updateActivity: 'renaming' to its own current name is not a conflict")
    void testPatchOwnNameIsFine() {
        when(activityRepository.findById(3L)).thenReturn(Optional.of(activityWith(3L, "Run")));
        when(activityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("Run", activityService.updateActivity(3L, patch("Run", null, 1.1, null, null)).getBody().name());
    }

    @Test
    @DisplayName("updateActivity: an empty body is a 400, an unknown id a 404")
    void testPatchEmptyAndMissing() {
        assertThrows(InvalidActivityUpdateException.class,
                () -> activityService.updateActivity(3L, patch(null, null, null, null, null)));

        when(activityRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ActivityNotFoundException.class,
                () -> activityService.updateActivity(9L, patch(null, null, null, null, true)));
    }

    @Test
    @DisplayName("deleteActivity: referenced by logs -> soft delete (DEACTIVATED), row kept")
    void testDeleteSoftWhenLogsReference() {
        Activity a = activityWith(3L, "Run");
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));
        when(activityLogRepository.existsByActivityId(3L)).thenReturn(true);

        var body = activityService.deleteActivity(3L).getBody();

        assertEquals(ActivityDeleteResponse.Result.DEACTIVATED, body.result());
        assertFalse(a.isActive());
        verify(activityRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteActivity: referenced only by a streak is still a soft delete")
    void testDeleteSoftWhenOnlyStreakReferences() {
        Activity a = activityWith(3L, "Run");
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));
        when(activityStreakRepository.existsByActivityId(3L)).thenReturn(true);

        assertEquals(ActivityDeleteResponse.Result.DEACTIVATED, activityService.deleteActivity(3L).getBody().result());
        verify(activityRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteActivity: deleting an already-inactive referenced activity is idempotent")
    void testDeleteSoftIsIdempotent() {
        Activity a = activityWith(3L, "Run");
        a.setActive(false);
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));
        when(activityLogRepository.existsByActivityId(3L)).thenReturn(true);

        assertEquals(ActivityDeleteResponse.Result.DEACTIVATED, activityService.deleteActivity(3L).getBody().result());
        verify(activityRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteActivity: unreferenced -> hard delete (REMOVED); unknown id -> 404")
    void testDeleteHardAndMissing() {
        Activity a = activityWith(3L, "Typo");
        when(activityRepository.findById(3L)).thenReturn(Optional.of(a));

        var body = activityService.deleteActivity(3L).getBody();

        assertEquals(ActivityDeleteResponse.Result.REMOVED, body.result());
        assertEquals("Typo", body.name());
        verify(activityRepository).delete(a);

        when(activityRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ActivityNotFoundException.class, () -> activityService.deleteActivity(9L));
    }

    @Test
    @DisplayName("includeInactive reads use the UNFILTERED lookups; false falls through to the active-only ones")
    void testIncludeInactiveReads() {
        Activity off = activityWith(3L, "Old");
        off.setActive(false);
        when(activityRepository.findById(3L)).thenReturn(Optional.of(off));
        when(activityRepository.findByName("Old")).thenReturn(Optional.of(off));
        when(activityRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(off));

        assertFalse(activityService.getActivityByIdOrName("3", true).getBody().active());
        assertFalse(activityService.getActivity("Old", true).getBody().active());
        assertEquals(1, activityService.getAllActivities(true).getBody().size());

        when(activityRepository.findAllByActiveTrue()).thenReturn(List.of());
        assertEquals(0, activityService.getAllActivities(false).getBody().size());
    }
}
