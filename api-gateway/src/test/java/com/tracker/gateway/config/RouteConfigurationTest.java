package com.tracker.gateway.config;

import com.tracker.gateway.standings.StandingsEnricher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final RouterFunction<ServerResponse> standings =
            config.standingsRoute(props, Mockito.mock(StandingsEnricher.class));

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

    private boolean claimedByStandings(String method, String path) {
        return standings.route(request(method, path)).isPresent();
    }

    @Test
    void rowReturningLeaderboardEndpointsGoToTheStandingsRoute_andOnlyThat() {
        for (String path : List.of(
                "/api/leaderboard",
                "/api/leaderboard/",
                "/api/leaderboard/activity/3",
                "/api/ranks/SUMMIT/leaderboard",
                "/api/ranks/me/leaderboard")) {
            assertThat(claimedByStandings("GET", path)).as("standings claims " + path).isTrue();
            assertThat(claimedByGamification("GET", path)).as("gamification must not claim " + path).isFalse();
            assertThat(claimedByActivity("GET", path)).as("activity must not claim " + path).isFalse();
        }
    }

    @Test
    void endpointsWithoutUserIdRowsAreNotRewritten() {
        // /leaderboard/me is a bare number and /ranks/me a single card: nothing to add a name to.
        for (String path : List.of(
                "/api/leaderboard/me",
                "/api/ranks/me",
                "/api/ranks",
                "/api/ranks/SUMMIT/members",
                "/api/level/user/5",
                "/api/notifications")) {
            assertThat(claimedByStandings("GET", path)).as("standings must not claim " + path).isFalse();
            assertThat(claimedByGamification("GET", path)).as("gamification still claims " + path).isTrue();
        }
    }

    @Test
    void onlyGetIsRewritten() {
        assertThat(claimedByStandings("POST", "/api/ranks/recompute")).isFalse();
        assertThat(claimedByGamification("POST", "/api/ranks/recompute")).isTrue();
        assertThat(claimedByStandings("POST", "/api/leaderboard")).isFalse();
        assertThat(claimedByGamification("POST", "/api/leaderboard")).isTrue();
    }

    @Test
    void successfulResponsesAreRewritten() {
        ServerResponse original = ServerResponse.ok().build();
        ServerResponse rewritten = ServerResponse.ok().header("X-Rewritten", "yes").build();

        ServerResponse result = RouteConfiguration.rewriteIfSuccessful(
                (req, resp) -> rewritten, request("GET", "/api/leaderboard"), original);

        assertThat(result).isSameAs(rewritten);
    }

    @Test
    void errorResponsesPassThroughUntouched_theRewriteIsNeverInvoked() {
        // A gamification error body is a ProblemDetail object, not an array: reading it as a list
        // would fail. The guard must hand it to the client as-is without invoking the rewrite.
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND,
                HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE)) {
            ServerResponse error = ServerResponse.status(status).build();
            AtomicBoolean invoked = new AtomicBoolean(false);

            ServerResponse result = RouteConfiguration.rewriteIfSuccessful(
                    (req, resp) -> {
                        invoked.set(true);
                        return resp;
                    }, request("GET", "/api/leaderboard"), error);

            assertThat(result).as("status " + status).isSameAs(error);
            assertThat(invoked.get()).as("rewrite invoked for " + status).isFalse();
        }
    }

    @Test
    void everyOtherPrefixKeepsItsRoute() {
        assertThat(claimedByActivity("GET", "/api/activity")).isTrue();
        assertThat(claimedByActivity("GET", "/api/insights/weekly")).isTrue();
        assertThat(claimedByGamification("GET", "/api/level/user/5")).isTrue();
        assertThat(claimedByGamification("GET", "/api/notifications")).isTrue();
        assertThat(claimedByGamification("GET", "/api/ranks/me")).isTrue();
    }
}
