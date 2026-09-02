package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.RefreshToken;
import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamily;
import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenStatus;
import com.pigeonkim.oauth2authorizationserver.exception.RefreshTokenReuseException;
import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenFamilyRepository;
import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokenRepo;
    private final RefreshTokenFamilyRepository familyRepo;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokenRepo,
                               RefreshTokenFamilyRepository familyRepo,
                               Clock clock) {
        this.tokenRepo = tokenRepo;
        this.familyRepo = familyRepo;
        this.clock = clock;
    }

    @Transactional
    public RefreshToken issue(Long accountId, String clientId, String jti, Instant expiresAt) {

        RefreshTokenFamily refreshTokenFamily = RefreshTokenFamily.start(accountId, clientId, clock.instant());

        familyRepo.save(refreshTokenFamily);

        RefreshToken refreshToken = RefreshToken.issue(refreshTokenFamily, jti, clock.instant(), expiresAt);
        tokenRepo.save(refreshToken);

        return refreshToken;
    }

    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public RefreshToken rotate(String presentedJti, String newJti, Instant newExpiresAt) {

        RefreshToken presented = tokenRepo.findById(presentedJti)
                .orElseThrow(() -> new IllegalArgumentException("unknown refresh token"));

        if (presented.getStatus() == RefreshTokenStatus.CONSUMED) {
            revokeFamily(presented.getFamily());

            throw new RefreshTokenReuseException(presentedJti);
        }

        if (presented.getStatus() != RefreshTokenStatus.ACTIVE) {
            throw new IllegalArgumentException("not active");
        }

        presented.consume();

        RefreshToken next = RefreshToken.issue(
                presented.getFamily(),
                newJti,
                clock.instant(),
                newExpiresAt);

        return tokenRepo.save(next);
    }

    private void revokeFamily(RefreshTokenFamily family) {

        family.revoke();
        tokenRepo.findByFamily(family)
                .forEach(RefreshToken::revoke);
    }

    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public void assertNotReused(String presentedJti) {

        Optional<RefreshToken> refreshToken = tokenRepo.findById(presentedJti);

        if (refreshToken.isEmpty()) {
            return;
        }

        if (refreshToken.get().getStatus() == RefreshTokenStatus.CONSUMED) {
            revokeFamily(refreshToken.get().getFamily());

            throw new RefreshTokenReuseException(presentedJti);
        }

        return;
    }
}
