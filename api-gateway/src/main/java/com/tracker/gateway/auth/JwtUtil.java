package com.tracker.gateway.auth;

import com.tracker.gateway.user.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String SECRET;

    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", Role.USER.name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }

    public String validateToken(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET).build()
                .parseSignedClaims(token)
                .getBody()
                .getSubject();
    }
}
