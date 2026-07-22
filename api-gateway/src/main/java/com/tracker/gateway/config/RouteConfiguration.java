package com.tracker.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class RouteConfiguration {

    @Bean
    public RouterFunction<ServerResponse> activityRoute(RateLimitProperties props) {
        var b = props.activity();
        return route("activity").route(path("/api/activity/**").or(path("/api/activitylog/**")), http())
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
                .route(path("/api/level/**").or(path("/api/threshold/**")).or(path("/api/leaderboard/**")).or(path("/api/notifications/**")).or(path("/api/ranks/**")), http())
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
