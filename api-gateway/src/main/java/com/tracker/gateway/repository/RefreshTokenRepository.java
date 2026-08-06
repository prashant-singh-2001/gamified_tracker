package com.tracker.gateway.repository;

import com.tracker.gateway.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByUser_Id(Long userId);

    /**
    * CAS (Compare And Set) - to eradicate race condition
    * Atomically marks a refresh token as used only if it has not already been consumed.
    *
    * @param token the refresh token string
    * @return the number of rows updated
    * 1 if successful,
    * 0 if the token was already used
    */
    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.isUsed = true
        WHERE rt.token = :token
        AND rt.isUsed = false
    """)
    int markUsedIfTokenNotYetUsed(@Param("token") String token);
}
