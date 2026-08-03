package com.pigeonkim.oauth2authorizationserver.domain;

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
                @UniqueConstraint(name = "uq_credential_provider", columnNames = {"provider", "provider_uid"})
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

    @Column(nullable = false)
    private boolean emailVerified;

    private Credential(Account account, CredentialType type, String email,
                       String passwordHash, String provider, String providerUid) {
        this.account = account;
        this.type = type;
        this.email = email;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUid = providerUid;
    }

    public static Credential basic(Account account, String email, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("EMAIL_PASSWORD requires passwordHash");

        if (email == null || email.isBlank())
            throw new IllegalArgumentException("EMAIL_PASSWORD requires email");

        return new Credential(account, CredentialType.EMAIL_PASSWORD, email, passwordHash, null, null);
    }

    public static Credential oauth(Account account, String email, String provider, String providerUid) {
        if (provider == null || providerUid == null)
            throw new IllegalArgumentException("OAUTH requires provider and providerUid");
        return new Credential(account, CredentialType.OAUTH, email, null, provider, providerUid);
    }

    @AssertTrue(message = "credential type/field combination is invalid")
    public boolean isFieldCombinationValid() {
        if (type == null) return false;
        return switch (type) {
            case EMAIL_PASSWORD -> email != null && passwordHash != null
                    && provider == null && providerUid == null;
            case OAUTH -> providerUid != null && provider != null
                    && passwordHash == null;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Credential other))
            return false;

        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }
}
