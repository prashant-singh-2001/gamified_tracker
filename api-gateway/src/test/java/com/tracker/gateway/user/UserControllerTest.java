package com.tracker.gateway.user;

import com.tracker.gateway.config.AuthRateLimitFilter;
import com.tracker.gateway.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    // @WebMvcTest picks up Filter components; this one needs a Redis-backed ProxyManager that is
    // not part of the slice (same reason AuthControllerTest mocks it).
    @MockBean
    private AuthRateLimitFilter authRateLimitFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // Filters are off in this slice, so the JWT the real chain would have authenticated is placed
    // in the security context by hand; @AuthenticationPrincipal reads it from there.
    private static void authenticateAs(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claims(c -> c.putAll(claims))
                .claim("sub", "ada@private.example")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static User user() {
        User u = new User();
        u.setId(7L);
        u.setFirstName("Ada");
        u.setLastName("Lovelace");
        u.setEmail("ada@private.example");
        u.setPassword("$2a$secret-hash");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void meReturnsTheCallersOwnProfile_andNothingSensitive() throws Exception {
        authenticateAs(Map.of("userId", 7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user()));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(content().string(not(containsString("private.example"))))
                .andExpect(content().string(not(containsString("secret-hash"))));
    }

    @Test
    void aValidTokenForADeletedAccountIs404ProblemDetail() throws Exception {
        authenticateAs(Map.of("userId", 7L));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    void aTokenWithoutAUserIdClaimIs401() throws Exception {
        authenticateAs(Map.of());

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
