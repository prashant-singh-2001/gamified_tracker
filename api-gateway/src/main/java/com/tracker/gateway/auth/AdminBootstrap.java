package com.tracker.gateway.auth;

import com.tracker.gateway.config.AdminBootstrapProperties;
import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.Role;
import com.tracker.gateway.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// #74: the only way to provision an ADMIN account now that /auth/register always yields
// USER (see AuthService.register). Off by default so `.env.example` keeps CI's
// `docker compose up` health gate green with no secrets configured.
@Component
@ConditionalOnProperty(prefix = "app.admin.bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AdminBootstrapProperties properties,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            throw new IllegalStateException(
                    "app.admin.bootstrap.enabled=true requires app.admin.bootstrap.email and .password to be set");
        }

        var existing = userRepository.findByEmail(properties.email());
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                userRepository.save(user);
                log.info("Admin bootstrap: promoted existing user {} to ADMIN", properties.email());
            } else {
                log.info("Admin bootstrap: {} is already ADMIN, nothing to do", properties.email());
            }
            return;
        }

        User admin = new User();
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setEmail(properties.email());
        admin.setPassword(passwordEncoder.encode(properties.password()));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Admin bootstrap: created ADMIN user {}", properties.email());
    }
}
