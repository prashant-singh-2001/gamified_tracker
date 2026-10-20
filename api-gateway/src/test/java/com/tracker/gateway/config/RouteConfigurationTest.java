package com.tracker.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins WHICH route claims a request. A wrong predicate here silently sends traffic to the wrong
 * service, and the symptom (a 404 from the other service) looks like an application bug.
 */
class RouteConfigurationTest {

    private final RateLimitProperties props = new RateLimitProperties(
            new RateLimitProperties.Bucket(100, 60),
            new RateLimitProperties.Bucket(100, 60),
            new RateLimitProperties.Bucket(10, 60));

    private final RouteConfiguration config = new RouteConfiguration();
    private final RouterFunction<ServerResponse> activity = config.activityRoute(props);
    private final RouterFunction<ServerResponse> gamification = config.gamificationRoute(props);

    private static ServerRequest request(String method, String path) {
        return ServerRequest.create(new MockHttpServletRequest(method, path), List.of());
    }

    private boolean claimedByActivity(String method, String path) {
        return activity.route(request(method, path)).isPresent();
    }

    private boolean claimedByGamification(String method, String path) {
        return gamification.route(request(method, path)).isPresent();
    }

    @Test
    void logOutcomeGoesToGamification_andOnlyGamification() {
        assertThat(claimedByGamification("GET", "/api/activitylog/42/outcome")).isTrue();
        assertThat(claimedByActivity("GET", "/api/activitylog/42/outcome")).isFalse();
    }

    @Test
    void otherActivityLogPathsStayWithActivityService() {
        for (String path : List.of(
                "/api/activitylog/42",
                "/api/activitylog/",
                "/api/activitylog/user/5",
                "/api/activitylog/review/flagged",
                "/api/activitylog/analytics/user/5/xp-over-time",
                // "*" is a single path segment: these are NOT the outcome route
                "/api/activitylog/user/5/outcome",
                "/api/activitylog/42/outcome/extra")) {
            assertThat(claimedByActivity("GET", path)).as("activity claims " + path).isTrue();
            assertThat(claimedByGamification("GET", path)).as("gamification must not claim " + path).isFalse();
        }
    }

    @Test
    void onlyGetIsRoutedToGamification_otherMethodsFallThroughToActivityService() {
        // The outcome resource is read-only. A POST must not reach gamification (which would answer
        // 405 for a path it does not own); activity-service answers it as it would any unknown path.
        assertThat(claimedByGamification("POST", "/api/activitylog/42/outcome")).isFalse();
        assertThat(claimedByActivity("POST", "/api/activitylog/42/outcome")).isTrue();
    }

    @Test
    void everyOtherPrefixKeepsItsRoute() {
        assertThat(claimedByActivity("GET", "/api/activity")).isTrue();
        assertThat(claimedByActivity("GET", "/api/insights/weekly")).isTrue();
        assertThat(claimedByGamification("GET", "/api/level/user/5")).isTrue();
        assertThat(claimedByGamification("GET", "/api/notifications")).isTrue();
        assertThat(claimedByGamification("GET", "/api/ranks/me")).isTrue();
        assertThat(claimedByGamification("GET", "/api/leaderboard")).isTrue();
    }
}
