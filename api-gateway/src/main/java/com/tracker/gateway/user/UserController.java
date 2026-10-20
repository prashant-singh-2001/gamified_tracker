package com.tracker.gateway.user;

import com.tracker.gateway.exception.InvalidCredentialsException;
import com.tracker.gateway.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Served by the gateway itself (it owns {@code User}); not proxied. Requires a token, like every
 * path outside {@code /auth/**} -- see SecurityConfig's {@code anyRequest().authenticated()}.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        // UserIdHeaderFilter already rejects a token with no userId claim; this is belt and braces.
        Object rawUserId = jwt.getClaim("userId");
        if (!(rawUserId instanceof Number userId)) {
            throw new InvalidCredentialsException("Token has no userId claim");
        }
        return userRepository.findById(userId.longValue())
                .map(UserProfile::of)
                .orElseThrow(UserNotFoundException::new);
    }

    /** A valid token for an account that has since been deleted: a 404, not a 500. */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    static class UserNotFoundException extends RuntimeException {
        UserNotFoundException() {
            super("User not found");
        }
    }
}
