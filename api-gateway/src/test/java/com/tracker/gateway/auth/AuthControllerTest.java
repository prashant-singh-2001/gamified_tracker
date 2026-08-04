package com.tracker.gateway.auth;

import com.tracker.gateway.config.AuthRateLimitFilter;
import com.tracker.gateway.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
}



