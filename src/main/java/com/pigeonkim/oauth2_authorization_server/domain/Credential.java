package com.pigeonkim.oauth2_authorization_server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "credential",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_credential_type_email", columnNames = {"type", "email"}),
                @UniqueConstraint(name = "uq_credential_provider",   columnNames = {"provider", "provider_uid"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    private CredentialType type;

    @Column(length = 255)
    private String email;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private String passwordHash;

    @Column(nullable = true)
    private String provider;

    @Column(nullable = true)
    private String providerUid;

    private Credential(Account account, CredentialType type, String email,
                       String passwordHash, String provider, String providerUid) {
        this.account = account;
        this.type = type;
        this.email = email;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUid = providerUid;
    }

    // ✅ BASIC 전용 진입로 — passwordHash 강제, provider류는 못 넣음
    public static Credential basic(Account account, String email, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("EMAIL_PASSWORD requires passwordHash");

        if (email == null || email.isBlank())
            throw new IllegalArgumentException("EMAIL_PASSWORD requires email");

        return new Credential(account, CredentialType.EMAIL_PASSWORD, email, passwordHash, null, null);
    }

    // ✅ KAKAO 전용 진입로 — provider/providerUid 강제, password는 못 넣음
    public static Credential kakao(Account account, String email, String provider, String providerUid) {
        if (provider == null || providerUid == null)
            throw new IllegalArgumentException("KAKAO requires provider and providerUid");
        return new Credential(account, CredentialType.KAKAO, email, null, provider, providerUid);
    }

    @AssertTrue(message = "credential type/field combination is invalid")
    public boolean isFieldCombinationValid() {
        if (type == null) return false;
        return switch (type) {
            case EMAIL_PASSWORD -> email != null && passwordHash != null
                    && provider == null && providerUid == null;   // BASIC은 email=식별자라 필수
            case KAKAO -> providerUid != null && provider != null
                    && passwordHash == null;                       // KAKAO는 email 선택 → 조건 없음
        };
    }
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Credential other))
            return false;

        return id != null && id.equals(other.id);   // id 없으면 동일성(==)만 인정
    }
    @Override
    public int hashCode() {
        return getClass().hashCode();   // 상수 — id 변해도 hashCode 불변 보장
    }
}
