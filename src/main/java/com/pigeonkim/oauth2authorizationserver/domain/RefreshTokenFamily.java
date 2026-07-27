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

    private Long accountId;    // 누구의 세션인가 (Account PK 값만 — 결합 줄이려 관계 대신 값)
    private String clientId;   // 어떤 클라이언트인가

    @Enumerated(EnumType.STRING)
    private RefreshTokenFamilyStatus status;

    private Instant createdAt;
    private static RefreshTokenFamilyStatus RefreshTokenFamilyStatus;

    protected RefreshTokenFamily() { }   // JPA용

    // 정적 팩토리 — 새 로그인 세션 시작 (Credential.basic() 방식)
    public static RefreshTokenFamily start(Long accountId, String clientId, Instant now) {
        // TODO: 새 인스턴스에 필드 세팅, status=ACTIVE, createdAt=now → 반환
        RefreshTokenFamily refreshTokenFamily = new RefreshTokenFamily();
        refreshTokenFamily.accountId = accountId;
        refreshTokenFamily.clientId = clientId;
        refreshTokenFamily.createdAt = now;
        refreshTokenFamily.status = RefreshTokenFamilyStatus.ACTIVE;

        return refreshTokenFamily;
    }

    // 도난 탐지 시 패밀리 무효화 (전이 메서드, 세터 금지)
    public void revoke() {
        // TODO: status=REVOKED  (이미 REVOKED면 멱등 처리할지 결정)
        this.status = RefreshTokenFamilyStatus.REVOKED;
    }

    // TODO: 필요한 getter (id, status 등)
}