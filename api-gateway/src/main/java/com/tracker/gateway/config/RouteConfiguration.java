package com.tracker.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
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
        return route("gamification")
                .route(path("/api/level/**").or(path("/api/threshold/**")).or(path("/api/leaderboard/**")).or(path("/api/notifications/**")).or(path("/api/ranks/**")).or(LOG_OUTCOME), http())
                .before(stripPrefix(1))
                .filter(lb("gamification-service"))
                .filter(rateLimit(
                        c -> c
                                .setCapacity(b.capacity())
                                .setPeriod(Duration.ofSeconds(b.periodSeconds()))
                                .setKeyResolver(RateLimitKeyResolver.byUserIdOrIp()))
                ).build();
    }
}
