package com.tracker.gateway.security;

import com.tracker.gateway.user.Role;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserIdHeaderFilter userIdHeaderFilter;

    public SecurityConfig(UserIdHeaderFilter userIdHeaderFilter) {
        this.userIdHeaderFilter = userIdHeaderFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtDecoder jwtDecoder,
                                           JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // #95: this chain also governs the container's INTERNAL ERROR dispatch to
                        // Boot's /error (spring.security.filter.dispatcher-types defaults to
                        // ASYNC,ERROR,REQUEST). On that dispatch the re-authenticating
                        // OncePerRequestFilters are skipped, so the context is anonymous, /error
                        // matches no permitAll entry below, and Spring Security writes its own
                        // error response OVER the real downstream status and body — masking every
                        // proxied error (a 404, a 500, anything) as 401/403. Must stay FIRST:
                        // matchers are evaluated in declaration order. This opens nothing —
                        // ERROR/FORWARD are internal dispatches, and the originating REQUEST
                        // dispatch was already authorized by the rules below.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/auth/**", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-resources/**", "/actuator/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/activity", "/api/activity/").hasRole("ADMIN")
                        // Session integrity (#67): the review queue and its approve/reject
                        // transitions are maintainer-only. activity-service has no Spring Security
                        // of its own, so this is the only place it can be enforced.
                        .requestMatchers("/api/activitylog/review/**").hasRole("ADMIN")
                        // #74: POST /level is a manual XP-award tool that writes straight into
                        // level_tracker. gamification-service has no Spring Security of its own,
                        // so this is the only place it can be enforced.
                        .requestMatchers(HttpMethod.POST, "/api/level", "/api/level/").hasRole("ADMIN")
                        // #81: threshold rows drive the leveling curve for every user. Only the
                        // create path is gated — POST /threshold/activity is a read that happens
                        // to use POST for its request body, not a write.
                        .requestMatchers(HttpMethod.POST, "/api/threshold", "/api/threshold/").hasRole("ADMIN")
                        // #83: full rank recompute is an expensive maintenance operation, not a
                        // user action.
                        .requestMatchers(HttpMethod.POST, "/api/ranks/recompute", "/api/ranks/recompute/")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(userIdHeaderFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String roleClaim = jwt.getClaimAsString("role");
            Role role = roleClaim != null ? Role.valueOf(roleClaim) : Role.USER;
            return List.of(new SimpleGrantedAuthority(role.authority()));
        });
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}