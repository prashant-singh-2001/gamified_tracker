package com.tracker.gateway.auth;

import com.tracker.gateway.repository.RefreshTokenRepository;
import com.tracker.gateway.user.RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(RefreshToken token) {

        if (!token.isRevoked()) {
            token.revoke();
            refreshTokenRepository.save(token);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId) {

        List<RefreshToken> tokens = refreshTokenRepository.findAllByUser_Id(userId);

        tokens.forEach(token -> {
            if (!token.isRevoked()) {
                token.revoke();
            }
        });

        refreshTokenRepository.saveAll(tokens);
    }
}
