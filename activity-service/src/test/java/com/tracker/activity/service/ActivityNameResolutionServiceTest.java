package com.tracker.activity.service;

import com.tracker.activity.config.ActivityNameMatchingProperties;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.Category;
import com.tracker.activity.domain.ActivityCandidate;
import com.tracker.activity.domain.ActivityMatch;
import com.tracker.activity.domain.ActivityMatcher;
import com.tracker.activity.domain.MatchField;
import com.tracker.activity.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityNameResolutionService Tests (issue #66)")
class ActivityNameResolutionServiceTest {

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityMatcher activityMatcher;

    // Matches the documented defaults (activity-name-matching.* in application.yaml).
    private final ActivityNameMatchingProperties enabledProperties =
            new ActivityNameMatchingProperties(true, 0.86, 0.05, 0.45, 3);

    private ActivityNameResolutionService resolutionService;

    @BeforeEach
    void setUp() {
        resolutionService = new ActivityNameResolutionService(activityRepository, activityMatcher, enabledProperties);
    }

    @Test
    @DisplayName("an AUTO_RESOLVED match maps back to the real Activity entity, not just its name")
    void autoResolved_mapsBackToTheRealEntity() {
        Activity running = activityFixture(1L, "Running", "Jogging, cardio", Category.HEALTH, true);
        when(activityRepository.findAll()).thenReturn(List.of(running));

        ActivityCandidate candidate = new ActivityCandidate("Running", "Jogging, cardio", "HEALTH", true);
        ActivityMatch topMatch = new ActivityMatch(candidate, 0.946, MatchField.NAME);
        when(activityMatcher.resolve(eq("Runnning"), any()))
                .thenReturn(new ActivityMatcher.Resolution(topMatch, List.of(topMatch), ActivityMatcher.Reason.AUTO_RESOLVED));

        ActivityNameResolutionService.NameResolution result = resolutionService.resolve("Runnning");

        assertTrue(result.resolved());
        assertSame(running, result.activity());
        assertEquals(0.946, result.score(), 1e-9);
        assertEquals(1, result.suggestions().size());
        assertEquals("Running", result.suggestions().get(0).name());
        assertEquals(Category.HEALTH, result.suggestions().get(0).category());
        assertTrue(result.suggestions().get(0).active());
    }

    @Test
    @DisplayName("the kill switch withholds the substitution but suggestions still ride the response")
    void autoResolveDisabled_withholdsSubstitutionButKeepsSuggestions() {
        Activity running = activityFixture(1L, "Running", "Jogging, cardio", Category.HEALTH, true);
        when(activityRepository.findAll()).thenReturn(List.of(running));

        ActivityCandidate candidate = new ActivityCandidate("Running", "Jogging, cardio", "HEALTH", true);
        ActivityMatch topMatch = new ActivityMatch(candidate, 0.946, MatchField.NAME);
        when(activityMatcher.resolve(eq("Runnning"), any()))
                .thenReturn(new ActivityMatcher.Resolution(topMatch, List.of(topMatch), ActivityMatcher.Reason.AUTO_RESOLVED));

        ActivityNameMatchingProperties disabled = new ActivityNameMatchingProperties(false, 0.86, 0.05, 0.45, 3);
        ActivityNameResolutionService serviceWithAutoResolveOff =
                new ActivityNameResolutionService(activityRepository, activityMatcher, disabled);

        ActivityNameResolutionService.NameResolution result = serviceWithAutoResolveOff.resolve("Runnning");

        assertFalse(result.resolved());
        assertNull(result.activity());
        assertEquals(ActivityMatcher.Reason.DISABLED, result.reason());
        assertEquals(1, result.suggestions().size());
        assertEquals("Running", result.suggestions().get(0).name());
    }

    @Test
    @DisplayName("a non-resolving reason (e.g. AMBIGUOUS) propagates unchanged, with suggestions but no activity")
    void nonResolvingReason_propagatesUnchanged() {
        Activity study = activityFixture(1L, "Study", null, Category.STUDY, true);
        Activity studying = activityFixture(2L, "Studying", null, Category.STUDY, true);
        when(activityRepository.findAll()).thenReturn(List.of(study, studying));

        ActivityMatch studyMatch = new ActivityMatch(
                new ActivityCandidate("Study", null, "STUDY", true), 0.943, MatchField.NAME);
        ActivityMatch studyingMatch = new ActivityMatch(
                new ActivityCandidate("Studying", null, "STUDY", true), 0.975, MatchField.NAME);
        when(activityMatcher.resolve(eq("Studyng"), any())).thenReturn(
                new ActivityMatcher.Resolution(null, List.of(studyingMatch, studyMatch), ActivityMatcher.Reason.AMBIGUOUS));

        ActivityNameResolutionService.NameResolution result = resolutionService.resolve("Studyng");

        assertFalse(result.resolved());
        assertNull(result.activity());
        assertEquals(0.0, result.score(), 1e-9);
        assertEquals(ActivityMatcher.Reason.AMBIGUOUS, result.reason());
        assertEquals(2, result.suggestions().size());
    }

    @Test
    @DisplayName("a null-named catalog row is filtered out of the candidate list before scoring")
    void nullNamedRow_isFilteredFromCandidates() {
        Activity valid = activityFixture(1L, "Running", "desc", Category.HEALTH, true);
        Activity unnamed = activityFixture(2L, null, "no name", Category.OTHER, true);
        when(activityRepository.findAll()).thenReturn(List.of(valid, unnamed));
        when(activityMatcher.resolve(anyString(), any()))
                .thenReturn(new ActivityMatcher.Resolution(null, List.of(), ActivityMatcher.Reason.NO_MATCH));

        resolutionService.resolve("query");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActivityCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(activityMatcher).resolve(eq("query"), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("Running", captor.getValue().get(0).name());
    }

    @Test
    @DisplayName("an empty catalog still calls the matcher, with an empty candidate list")
    void emptyCatalog_stillCallsMatcherWithEmptyList() {
        when(activityRepository.findAll()).thenReturn(List.of());
        when(activityMatcher.resolve(anyString(), any()))
                .thenReturn(new ActivityMatcher.Resolution(null, List.of(), ActivityMatcher.Reason.NO_MATCH));

        ActivityNameResolutionService.NameResolution result = resolutionService.resolve("anything");

        assertFalse(result.resolved());
        assertEquals(ActivityMatcher.Reason.NO_MATCH, result.reason());
        verify(activityRepository).findAll();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActivityCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(activityMatcher).resolve(eq("anything"), captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    private static Activity activityFixture(Long id, String name, String description, Category category, boolean active) {
        return Activity.builder()
                .id(id)
                .name(name)
                .description(description)
                .category(category)
                .active(active)
                .build();
    }
}
