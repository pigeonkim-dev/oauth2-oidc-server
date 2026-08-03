package com.pigeonkim.oauth2authorizationserver.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "refresh_token_family")
@Getter
public class RefreshTokenFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private String clientId;

    @Enumerated(EnumType.STRING)
    private RefreshTokenFamilyStatus status;

    private Instant createdAt;

    protected RefreshTokenFamily() {
    }

    public static RefreshTokenFamily start(Long accountId, String clientId, Instant now) {
        RefreshTokenFamily refreshTokenFamily = new RefreshTokenFamily();
        refreshTokenFamily.accountId = accountId;
        refreshTokenFamily.clientId = clientId;
        refreshTokenFamily.createdAt = now;
        refreshTokenFamily.status = RefreshTokenFamilyStatus.ACTIVE;

        return refreshTokenFamily;
    }

    public void revoke() {
        this.status = RefreshTokenFamilyStatus.REVOKED;
    }
}