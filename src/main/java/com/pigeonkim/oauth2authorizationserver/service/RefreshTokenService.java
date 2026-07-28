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
    private final Clock clock;                       // T2처럼 주입 (테스트=Clock.fixed)

    public RefreshTokenService(RefreshTokenRepository tokenRepo,
                               RefreshTokenFamilyRepository familyRepo,
                               Clock clock) {
        this.tokenRepo = tokenRepo;
        this.familyRepo = familyRepo;
        this.clock = clock;
    }

    /**
     * 최초 발급: 새 패밀리 + 첫 refresh 토큰
     */
    @Transactional
    public RefreshToken issue(Long accountId, String clientId, String jti, Instant expiresAt) {
        // TODO:
        //  1) RefreshTokenFamily.start(accountId, clientId, clock.instant()) → familyRepo.save
        //  2) RefreshToken.issue(family, jti, clock.instant(), expiresAt) → tokenRepo.save 후 반환

        RefreshTokenFamily refreshTokenFamily = RefreshTokenFamily.start(accountId, clientId, clock.instant());

        familyRepo.save(refreshTokenFamily);

        RefreshToken refreshToken = RefreshToken.issue(refreshTokenFamily, jti, clock.instant(), expiresAt);
        tokenRepo.save(refreshToken);

        return refreshToken;
    }

    /**
     * 회전 + 재사용 탐지 (★T4 핵심)
     */
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)   // ← 아래 ★설명 꼭 읽기
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

        // TODO — 여기가 심장. presented.getStatus() 로 분기:
        //   CONSUMED → 🚨도난! revokeFamily(presented.getFamily());
        //              throw new RefreshTokenReuseException(presentedJti);
        //   ACTIVE   → 정상 회전:
        //              presented.consume();                          // ACTIVE→CONSUMED
        //              RefreshToken next = RefreshToken.issue(
        //                    presented.getFamily(), newJti, clock.instant(), newExpiresAt);
        //              return tokenRepo.save(next);
        //   그 외(REVOKED 등) → throw new IllegalArgumentException("not active");
    }

    /**
     * 패밀리 + 체인 전부 무효 (방식 A)
     */
    private void revokeFamily(RefreshTokenFamily family) {
        // TODO: family.revoke();
        //       tokenRepo.findByFamily(family).forEach(RefreshToken::revoke);

        family.revoke();
        tokenRepo.findByFamily(family)
                .forEach(RefreshToken::revoke);
    }

    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public void assertNotReused(String presentedJti) {
        // TODO:
        //  tokenRepo.findById(presentedJti) 로 조회해서
        //    - 비어있음(대장부에 없음) → return (초기발급 토큰 등. 아래 '남은 TODO' 참고)
        //    - CONSUMED → revokeFamily(해당 family) + throw new RefreshTokenReuseException(presentedJti)
        //    - 그 외(ACTIVE 등) → return

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
