package com.pigeonkim.oauth2authorizationserver.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_token")
@Getter
public class RefreshToken {

    @Id
    private String jti;    // JWT ID = 이 refresh 토큰 고유값 (SAS가 부여)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private RefreshTokenFamily family;

    @Enumerated(EnumType.STRING)
    private RefreshTokenStatus status;

    private Instant issuedAt;
    private Instant expiresAt;

    @Version
    private long version;

    protected RefreshToken() {
    }

    // 발급
    public static RefreshToken issue(RefreshTokenFamily family, String jti,
                                     Instant issuedAt, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.family = family;
        refreshToken.jti = jti;
        refreshToken.issuedAt = issuedAt;
        refreshToken.expiresAt = expiresAt;
        refreshToken.status = RefreshTokenStatus.ACTIVE;

        return refreshToken;
    }

    // 정상 회전 — 이 토큰 소비. ★reuse-detection의 씨앗
    public void consume() {
        // TODO: status가 ACTIVE가 아니면? → 이미 CONSUMED인데 또 consume = 도난 신호
        //   여기서 IllegalStateException 등으로 "이미 소비됨"을 알리고,
        //   그 신호를 서비스가 받아 family.revoke()를 호출하게 됨 (서비스는 다음 세션)
        //   정상이면 status=CONSUMED

        if (status != RefreshTokenStatus.ACTIVE) {
            throw new IllegalStateException("RefreshToken is not active");
        }

        this.status = RefreshTokenStatus.CONSUMED;
    }

    // 패밀리 무효화에 딸려 무효
    public void revoke() {
        // TODO: status=REVOKED

        this.status = RefreshTokenStatus.REVOKED;
    }
    // TODO: getter (jti, status, family 등)
}
