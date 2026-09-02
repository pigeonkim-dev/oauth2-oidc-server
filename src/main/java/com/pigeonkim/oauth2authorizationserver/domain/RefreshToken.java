package com.pigeonkim.oauth2authorizationserver.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "refresh_token",
        indexes = {
                @Index(name = "idx_rt_family", columnList = "family_id"),
                @Index(name = "idx_rt_expires", columnList = "expires_at")
        })
@Getter
public class RefreshToken {

    @Id
    private String jti;

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

    public void consume() {

        if (status != RefreshTokenStatus.ACTIVE) {
            throw new IllegalStateException("RefreshToken is not active");
        }

        this.status = RefreshTokenStatus.CONSUMED;
    }

    public void revoke() {

        this.status = RefreshTokenStatus.REVOKED;
    }
}
