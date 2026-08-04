package com.tracker.gateway.auth;

import com.tracker.gateway.user.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    @Test
    void shouldGenerateAndValidateToken() throws Exception {
        JwtUtil jwtUtil = new JwtUtil();
        setPrivateField(jwtUtil, "SECRET", "my-secret-key-123456789012345678901234567890");
        setPrivateField(jwtUtil, "expiration", 10000L);

        String token = jwtUtil.generateToken("john@example.com", Role.USER, 123L);

        Claims claims = jwtUtil.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("john@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo(Role.USER.name());
        assertThat(claims.get("userId", Integer.class)).isEqualTo(123);
    }

    @Test
    void shouldThrowWhenTokenIsInvalid() throws Exception {
        JwtUtil jwtUtil = new JwtUtil();
        setPrivateField(jwtUtil, "SECRET", "my-secret-key-123456789012345678901234567890");
        setPrivateField(jwtUtil, "expiration", 10000L);

        assertThrows(Exception.class, () -> jwtUtil.validateToken("invalid.token.value"));
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
