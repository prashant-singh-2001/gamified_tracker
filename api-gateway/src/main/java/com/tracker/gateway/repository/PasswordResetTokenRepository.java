package com.tracker.gateway.repository;

import com.tracker.gateway.user.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Compare-and-set redemption: marks the token used only if it is still unused and unexpired, in
     * one statement, so two concurrent resets with the same token cannot both succeed.
     *
     * @return 1 if this call redeemed the token, 0 if it was unknown, already used, or expired
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.tokenHash = :tokenHash
            AND t.usedAt IS NULL
            AND t.expiresAt > :now
            """)
    int markUsedIfValid(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** Retires every still-unused token of a user, so only the most recently issued one is live. */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.user.id = :userId
            AND t.usedAt IS NULL
            """)
    int invalidateUnusedForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
