package com.tracker.gateway.auth;

import com.tracker.gateway.dto.AuthResponse;
import com.tracker.gateway.dto.LoginRequest;
import com.tracker.gateway.dto.RegisterRequest;
import com.tracker.gateway.exception.InvalidCredentialsException;
import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.RefreshToken;
import com.tracker.gateway.user.Role;
import com.tracker.gateway.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    @Value("${jwt.expiration}")
    private long expiration;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse register(RegisterRequest request) {

        var user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());

        // Encrypt password before saving
        user.setPassword(passwordEncoder.encode(request.password()));

        user.setRole(request.role() != null ? request.role() : Role.USER);
        userRepository.save(user);

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId());
        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    public AuthResponse login(LoginRequest req) {

        var user = userRepository.findByEmail(req.email())
                .orElseThrow(InvalidCredentialsException::new);

        // Compare encrypted password
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId());
        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        // Validate the refresh token
        RefreshToken oldToken = refreshTokenService.validateRefreshToken(refreshToken);
        User user = oldToken.getUser();

        // Generate new Refresh Token
        RefreshToken newRefreshToken = refreshTokenService.generateRefreshToken(user);

        // Generate new Access Token
        String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId());

        return new AuthResponse(newAccessToken, newRefreshToken.getToken());
    }
}