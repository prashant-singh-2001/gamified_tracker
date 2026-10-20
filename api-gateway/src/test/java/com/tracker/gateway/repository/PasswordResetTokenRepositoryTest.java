package com.tracker.gateway.repository;

import com.tracker.gateway.user.PasswordResetToken;
import com.tracker.gateway.user.Role;
import com.tracker.gateway.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

// @Autowired field injection, matching the repo's other @DataJpaTest classes.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository repository;
    @Autowired
    private TestEntityManager em;

    private User user;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder().firstName("Ada").lastName("L").email("ada@example.com")
                .password("x").role(Role.USER).build());
    }

    private PasswordResetToken token(String hash, Instant expiresAt) {
        return em.persist(PasswordResetToken.builder().tokenHash(hash).user(user)
                .expiresAt(expiresAt).createdAt(Instant.now()).build());
    }

    private Instant usedAt(String hash) {
        em.flush();
        em.clear();
        return repository.findByTokenHash(hash).orElseThrow().getUsedAt();
    }

    @Test
    void redeemsAFreshTokenExactlyOnce() {
        token("h1", Instant.now().plusSeconds(600));

        assertEquals(1, repository.markUsedIfValid("h1", Instant.now()));
        assertEquals(0, repository.markUsedIfValid("h1", Instant.now()), "second redemption must not match");
        assertNotNull(usedAt("h1"));
    }

    @Test
    void doesNotRedeemAnExpiredToken() {
        token("h2", Instant.now().minusSeconds(1));

        assertEquals(0, repository.markUsedIfValid("h2", Instant.now()));
        assertNull(usedAt("h2"));
    }

    @Test
    void doesNotRedeemAnUnknownToken() {
        assertEquals(0, repository.markUsedIfValid("missing", Instant.now()));
    }

    @Test
    void invalidateRetiresOnlyThisUsersUnusedTokens() {
        token("a", Instant.now().plusSeconds(600));
        token("b", Instant.now().plusSeconds(600));
        var other = em.persist(User.builder().firstName("B").lastName("B").email("b@example.com")
                .password("x").role(Role.USER).build());
        em.persist(PasswordResetToken.builder().tokenHash("c").user(other)
                .expiresAt(Instant.now().plusSeconds(600)).createdAt(Instant.now()).build());

        assertEquals(2, repository.invalidateUnusedForUser(user.getId(), Instant.now()));

        assertNotNull(usedAt("a"));
        assertNotNull(usedAt("b"));
        assertNull(usedAt("c"));
        assertEquals(0, repository.markUsedIfValid("a", Instant.now()), "a retired token no longer redeems");
    }
}
