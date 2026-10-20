package com.tracker.gateway.auth;

import com.tracker.gateway.config.AuthRateLimitFilter;
import com.tracker.gateway.dto.AuthResponse;
import com.tracker.gateway.exception.EmailAlreadyRegisteredException;
import com.tracker.gateway.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    // AuthRateLimitFilter is a @Component Filter, so @WebMvcTest's web-layer slicing
    // auto-detects and tries to construct it even though addFilters=false keeps it from
    // actually running requests. Its real constructor needs a ProxyManager<String> bean
    // (backed by Redis), which isn't part of this slice - mock the filter bean itself
    // rather than its Bucket4j dependencies, mirroring how JwtFilter's only dependency
    // (JwtUtil, above) is already satisfied.
    @MockBean
    private AuthRateLimitFilter authRateLimitFilter;

    @Test
    void testRegister() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("access-token-123", "refresh-token-123"));

        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john@example.com\",\"password\":\"pass123\",\"role\":\"USER\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"accessToken\":\"access-token-123\",\"refreshToken\":\"refresh-token-123\"}"));
    }

    @Test
    void testLogin() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("access-token-456", "refresh-token-456"));

        String json = "{\"email\":\"john@example.com\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"accessToken\":\"access-token-456\",\"refreshToken\":\"refresh-token-456\"}"));
    }

    @Test
    void testRefresh() throws Exception {
        when(authService.refresh(any())).thenReturn(new AuthResponse("new-access-token", "new-refresh-token"));

        String json = "{\"refreshToken\":\"existing-refresh-token\"}";

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"accessToken\":\"new-access-token\",\"refreshToken\":\"new-refresh-token\"}"));
    }

    @Test
    void testRegisterDuplicateEmailReturns409ProblemDetail() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john@example.com\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void testRegisterMissingEmailIsRejected() throws Exception {
        // @Email alone lets a missing email through; @NotBlank is what rejects it.
        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("email is required"))
                .andExpect(jsonPath("$.errors.length()").value(1));

        verifyNoInteractions(authService);
    }

    @Test
    void testRegisterMalformedEmailIsRejectedWithFormatMessage() throws Exception {
        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"not-an-email\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("email should be formatted and required"));
    }

    @Test
    void testRegisterReportsEveryInvalidFieldAtOnce() throws Exception {
        String json = "{\"firstName\":\"\",\"lastName\":\" \",\"email\":\"\",\"password\":\"\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.firstName").value("firstName is required"))
                .andExpect(jsonPath("$.errors.lastName").value("lastName is required"))
                .andExpect(jsonPath("$.errors.email").value("email is required"))
                .andExpect(jsonPath("$.errors.password").value("password is required"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                // detail is the joined messages (sorted by field), a short human-readable summary
                .andExpect(jsonPath("$.detail").value(
                        "email is required; firstName is required; lastName is required; password is required"));
    }

    @Test
    void testPaddedEmailIsTrimmedBeforeValidationSoItIsAccepted() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("a", "r"));
        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"  John@Example.com \",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void testLoginBlankPasswordReturnsFieldError() throws Exception {
        String json = "{\"email\":\"john@example.com\",\"password\":\"\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("password is required"));
    }

    @Test
    void testLoginMalformedEmailIsStillA400_nowWithTheFieldNamed() throws Exception {
        // Unchanged status: a malformed login email was already a 400 (LoginRequest's @Email was
        // live). What changed is the body, which now says which field.
        String json = "{\"email\":\"not-an-email\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("email should be formatted and required"));

        verifyNoInteractions(authService);
    }

    @Test
    void testLoginWrongCredentialsKeepTheUniform401() throws Exception {
        // The frontend keys on this; validation must not intercept a well-formed login.
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());
        String json = "{\"email\":\"john@example.com\",\"password\":\"wrong\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void testRefreshBlankTokenReturnsFieldError() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh Token Is Required"));
    }
}
