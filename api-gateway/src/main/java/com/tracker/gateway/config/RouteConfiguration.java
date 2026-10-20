package com.tracker.gateway.config;

import org.springframework.context.annotation.Bean;
import com.tracker.gateway.standings.StandingsEnricher;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BodyFilterFunctions.modifyResponseBody;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class RouteConfiguration {

    // GET /api/activitylog/{id}/outcome is gamification's answer (did applying this log's XP level
    // me up?) but lives under the activitylog resource the client is asking about. Both routes
    // reference this one predicate -- activityRoute excludes it, gamificationRoute claims it -- so
    // they cannot overlap whatever order the two RouterFunction beans are evaluated in.
    private static final RequestPredicate LOG_OUTCOME = GET("/api/activitylog/*/outcome");

    // The leaderboard-style endpoints whose rows carry a userId: global, per-activity, per-tier and
    // "my tier" (the "*" in ranks/*/leaderboard is either a tier name or "me"). Their responses get
    // a displayName added in the gateway (standingsRoute), which owns User. Deliberately NOT
    // /api/leaderboard/me, which returns a bare number. gamificationRoute excludes this predicate and
    // standingsRoute claims it, so the two cannot overlap.
    private static final RequestPredicate STANDINGS_ROWS = GET("/api/leaderboard")
            .or(GET("/api/leaderboard/"))
            .or(GET("/api/leaderboard/activity/*"))
            .or(GET("/api/ranks/*/leaderboard"));

    @Bean
    public RouterFunction<ServerResponse> activityRoute(RateLimitProperties props) {
        var b = props.activity();
        // /api/insights/** rides this route (issue #65) rather than getting its own bean -- a new
        // bean would need its own RateLimitProperties.Bucket + YAML + .env.example vars or it NPEs
        // at startup, and the feature doesn't need a separate throttle from activity/activitylog.
        return route("activity").route(path("/api/activity/**").or(path("/api/activitylog/**").and(LOG_OUTCOME.negate())).or(path("/api/insights/**")), http())
                .before(rewritePath("^/api/(activity|activitylog)/?$", "/$1/"))
                .before(rewritePath("^/api/(.*)$", "/$1"))
                .filter(lb("activity-service"))
                .filter(rateLimit(
                        c -> c
                                .setCapacity(b.capacity())
                                .setPeriod(Duration.ofSeconds(b.periodSeconds()))
                                .setKeyResolver(RateLimitKeyResolver.byUserIdOrIp()))
                ).build();
    }

    @Bean
    public RouterFunction<ServerResponse> gamificationRoute(RateLimitProperties props) {
        var b = props.gamification();
        RequestPredicate gamificationPaths = path("/api/level/**").or(path("/api/threshold/**"))
                .or(path("/api/leaderboard/**")).or(path("/api/notifications/**")).or(path("/api/ranks/**"));
        return route("gamification")
                .route(gamificationPaths.and(STANDINGS_ROWS.negate()).or(LOG_OUTCOME), http())
                .before(stripPrefix(1))
                .filter(lb("gamification-service"))
                .filter(rateLimit(
                        c -> c
                                .setCapacity(b.capacity())
                                .setPeriod(Duration.ofSeconds(b.periodSeconds()))
                                .setKeyResolver(RateLimitKeyResolver.byUserIdOrIp()))
                ).build();
    }

    // Same upstream, rewrite and throttle as gamificationRoute (same bucket properties and key
    // resolver, so these requests count against the same per-user budget) -- but the response body
    // is rewritten to add each row's displayName. A separate route so that rewrite (which buffers
    // and re-serialises the body) only ever runs for the four endpoints that return rows.
    @Bean
    public RouterFunction<ServerResponse> standingsRoute(RateLimitProperties props, StandingsEnricher enricher) {
        var b = props.gamification();
        BiFunction<ServerRequest, ServerResponse, ServerResponse> addDisplayNames = modifyResponseBody(
                List.class, List.class, MediaType.APPLICATION_JSON_VALUE,
                (request, response, rows) -> enricher.withDisplayNames(rows));
        return route("standings")
                .route(STANDINGS_ROWS, http())
                .before(stripPrefix(1))
                .filter(lb("gamification-service"))
                .filter(rateLimit(
                        c -> c
                                .setCapacity(b.capacity())
                                .setPeriod(Duration.ofSeconds(b.periodSeconds()))
                                .setKeyResolver(RateLimitKeyResolver.byUserIdOrIp()))
                )
                .after((request, response) -> rewriteIfSuccessful(addDisplayNames, request, response))
                .build();
    }

    // Only successful responses are rewritten: an error body from gamification is a ProblemDetail
    // object, not an array, and must reach the client untouched (the rewrite would fail to read it).
    static ServerResponse rewriteIfSuccessful(BiFunction<ServerRequest, ServerResponse, ServerResponse> rewrite,
                                              ServerRequest request, ServerResponse response) {
        return response.statusCode().is2xxSuccessful() ? rewrite.apply(request, response) : response;
    }
}
