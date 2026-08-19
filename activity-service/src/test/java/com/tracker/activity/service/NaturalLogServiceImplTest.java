package com.tracker.activity.service;

import com.tracker.activity.config.ActivityNameMatchingProperties;
import com.tracker.activity.config.NaturalLogProperties;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.Category;
import com.tracker.activity.domain.ActivityMatcher;
import com.tracker.activity.domain.LexicalActivityNameScorer;
import com.tracker.activity.domain.LogIntentResolver;
import com.tracker.activity.domain.NaturalLanguageLogParser;
import com.tracker.activity.domain.ParsedLogIntent;
import com.tracker.activity.domain.TimeOfDay;
import com.tracker.activity.dto.DraftStatus;
import com.tracker.activity.dto.NaturalLogDraftResponse;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.service.impl.NaturalLogServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Natural Log Service Tests (issue #70)")
class NaturalLogServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 8, 14, 14, 0).atZone(ZONE).toInstant(), ZONE);
    private static final long MAX_DURATION_MINUTES = 1440;

    @Mock
    private ActivityRepository activityRepository;

    private StubParser parser;
    private LogIntentResolver logIntentResolver;
    private NaturalLogProperties naturalLogProperties;
    private ActivityNameResolutionService activityNameResolutionService;
    private MeterRegistry meterRegistry;
    private NaturalLogServiceImpl naturalLogService;

    private Activity studyActivity;

    @BeforeEach
    void setUp() {
        studyActivity = Activity.builder().id(1L).name("Study").category(Category.STUDY)
                .xpMultiplier(1.5).active(true).build();

        parser = new StubParser();
        logIntentResolver = new LogIntentResolver(FIXED_CLOCK, MAX_DURATION_MINUTES);
        naturalLogProperties = new NaturalLogProperties(true, 500);
        meterRegistry = new SimpleMeterRegistry();

        // Real #66 composition -- not mocked -- so the name-preview tests below prove genuine
        // composition through the actual matcher, not a stand-in.
        ActivityMatcher activityMatcher = new ActivityMatcher(new LexicalActivityNameScorer(), 0.86, 0.05, 0.45, 3);
        activityNameResolutionService = new ActivityNameResolutionService(
                activityRepository, activityMatcher, new ActivityNameMatchingProperties(true, 0.86, 0.05, 0.45, 3));

        naturalLogService = new NaturalLogServiceImpl(
                parser, logIntentResolver, naturalLogProperties,
                activityRepository, activityNameResolutionService, meterRegistry);

        lenient().when(activityRepository.findAll()).thenReturn(List.of(studyActivity));
    }

    @Test
    @DisplayName("flag off, parser returns nothing -> DISABLED, nothing written")
    void parseNaturalLog_flagOff_reportsDisabled() {
        NaturalLogProperties disabledProperties = new NaturalLogProperties(false, 500);
        NaturalLogServiceImpl serviceWithFlagOff = new NaturalLogServiceImpl(
                parser, logIntentResolver, disabledProperties, activityRepository, activityNameResolutionService, meterRegistry);

        NaturalLogDraftResponse response = serviceWithFlagOff.parseNaturalLog(1L, "studied for 30 minutes").getBody();

        assertNotNull(response);
        assertEquals(DraftStatus.DISABLED, response.status());
        assertNull(response.draft());
    }

    @Test
    @DisplayName("flag on but the parser produces nothing -> UNAVAILABLE")
    void parseNaturalLog_flagOnEmptyResult_reportsUnavailable() {
        // parser's default result is Optional.empty()

        NaturalLogDraftResponse response = naturalLogService.parseNaturalLog(1L, "???").getBody();

        assertNotNull(response);
        assertEquals(DraftStatus.UNAVAILABLE, response.status());
        assertNull(response.draft());
    }

    @Test
    @DisplayName("a parser that throws degrades to UNAVAILABLE instead of propagating")
    void parseNaturalLog_parserThrows_degradesGracefully_notRethrown() {
        parser.toThrow = new RuntimeException("model timeout");

        ResponseEntity<NaturalLogDraftResponse> responseEntity =
                assertDoesNotThrow(() -> naturalLogService.parseNaturalLog(1L, "studied for 30 minutes"));

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        NaturalLogDraftResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(DraftStatus.UNAVAILABLE, response.status());
        assertEquals(1, meterRegistry.get("activity.log.natural.parse").tag("outcome", "UNAVAILABLE").counter().count(), 1e-9);
    }

    @Test
    @DisplayName("a parsed intent the resolver rejects (no duration) -> NEEDS_CLARIFICATION, nothing invented")
    void parseNaturalLog_resolverRejects_reportsNeedsClarification() {
        parser.result = Optional.of(new ParsedLogIntent("studying", 0, null, null, TimeOfDay.MORNING, null, null));

        NaturalLogDraftResponse response = naturalLogService.parseNaturalLog(1L, "studied this morning").getBody();

        assertNotNull(response);
        assertEquals(DraftStatus.NEEDS_CLARIFICATION, response.status());
        assertNull(response.draft());
        assertNotNull(response.interpretation());
        assertEquals(1, meterRegistry.get("activity.log.natural.parse").tag("outcome", "NEEDS_CLARIFICATION").counter().count(), 1e-9);
    }

    @Test
    @DisplayName("a resolvable intent -> PARSED, with a committable draft and an interpretation")
    void parseNaturalLog_resolvedIntent_reportsParsedWithDraft() {
        parser.result = Optional.of(new ParsedLogIntent("studying", 0, null, null, TimeOfDay.MORNING, 90, "Spring Boot"));

        NaturalLogDraftResponse response = naturalLogService.parseNaturalLog(1L, "studied Spring Boot this morning").getBody();

        assertNotNull(response);
        assertEquals(DraftStatus.PARSED, response.status());
        assertNotNull(response.draft());
        assertEquals("studying", response.draft().activityName());
        assertEquals("Spring Boot", response.draft().notes());
        assertNull(response.draft().createdAt());
        assertNotNull(response.interpretation());
        assertEquals(1, meterRegistry.get("activity.log.natural.parse").tag("outcome", "PARSED").counter().count(), 1e-9);
    }

    @Test
    @DisplayName("name resolution composes through the real #66 matcher: \"studying\" resolves to catalog \"Study\"")
    void parseNaturalLog_nameResolutionComposesWithRealMatcher() {
        when(activityRepository.findByName("studying")).thenReturn(Optional.empty());
        parser.result = Optional.of(new ParsedLogIntent("studying", 0, null, null, TimeOfDay.MORNING, 90, null));

        NaturalLogDraftResponse response = naturalLogService.parseNaturalLog(1L, "studied this morning").getBody();

        assertNotNull(response);
        assertNotNull(response.nameResolution());
        assertEquals("studying", response.nameResolution().requestedName());
        assertEquals("Study", response.nameResolution().resolvedName());
        // The draft itself is untouched -- still the raw text the model produced, not the resolved
        // catalog name. Committing it is what actually resolves the name, same as any other POST.
        assertEquals("studying", response.draft().activityName());
    }

    @Test
    @DisplayName("an exact catalog match needs no preview -- nameResolution and suggestions are empty")
    void parseNaturalLog_exactNameMatch_noPreviewNeeded() {
        when(activityRepository.findByName("Study")).thenReturn(Optional.of(studyActivity));
        parser.result = Optional.of(new ParsedLogIntent("Study", 0, null, null, TimeOfDay.MORNING, 90, null));

        NaturalLogDraftResponse response = naturalLogService.parseNaturalLog(1L, "Study this morning").getBody();

        assertNotNull(response);
        assertNull(response.nameResolution());
        assertTrue(response.suggestions().isEmpty());
    }

    /** Provider-agnostic by design (not a Mockito mock) -- stays valid whichever backend is configured. */
    private static final class StubParser implements NaturalLanguageLogParser {
        private Optional<ParsedLogIntent> result = Optional.empty();
        private RuntimeException toThrow;

        @Override
        public Optional<ParsedLogIntent> parse(String text) {
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }
    }
}
