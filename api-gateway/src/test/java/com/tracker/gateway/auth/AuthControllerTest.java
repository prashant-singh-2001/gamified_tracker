package com.tracker.gateway.auth;

import com.tracker.gateway.config.AuthRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtUtil jwtUtil;

    // AuthRateLimitFilter is a @Component Filter, so @WebMvcTest's web-layer slicing
    // auto-detects and tries to construct it even though addFilters=false keeps it from
    // actually running requests. Its real constructor needs a ProxyManager<String> bean
    // (backed by Redis), which isn't part of this slice - mock the filter bean itself
    // rather than its Bucket4j dependencies, mirroring how JwtFilter's only dependency
    // (JwtUtil, above) is already satisfied.
    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @Test
    void testRegister() throws Exception {
        when(authService.register(any())).thenReturn("token123");

        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john@example.com\",\"password\":\"pass123\",\"role\":\"USER\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void testLogin() throws Exception {
        when(authService.login(any())).thenReturn("token456");

        String json = "{\"email\":\"john@example.com\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    // The constraints on LoginRequest/RegisterRequest predate the validation starter, so until it
    // was added these bodies were all accepted and reached AuthService.

    @Test
    void register_withoutRole_defaultsToUserInsteadOfFailingValidation() throws Exception {
        when(authService.register(any())).thenReturn("token123");

        // role omitted deliberately: AuthService defaults it, so the constraint must not require it
        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john@example.com\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void register_rejectsBlankFieldsWithJoinedFieldMessages() throws Exception {
        String json = "{\"firstName\":\"\",\"lastName\":\"\",\"email\":\"not-an-email\",\"password\":\"\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("firstName is required")))
                .andExpect(jsonPath("$.detail").value(containsString("password is required")));

        verify(authService, never()).register(any());
    }

    @Test
    void login_rejectsMalformedEmailBeforeReachingTheService() throws Exception {
        String json = "{\"email\":\"nope\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_rejectsMissingEmail() throws Exception {
        // @Email alone passes on null — @NotBlank is what makes this a 400.
        String json = "{\"password\":\"pass123\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("email is required")));

        verify(authService, never()).login(any());
    }
}



