package com.pigeonkim.oauth2authorizationserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "verification_challenge",
        indexes = {
                @Index(name = "idx_vc_lookup", columnList = "account_id, channel, purpose, status"),
                @Index(name = "idx_vc_expires", columnList = "expires_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationChallenge {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationPurpose purpose;

    @Column(nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = true)
    private Instant lastAttemptAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = true)
    private Instant consumedAt;

    @CreationTimestamp
    private Instant createdAt;

    private VerificationChallenge(Account account, VerificationChannel channel, VerificationPurpose purpose,
                                  String codeHash, Instant expiresAt) {
        this.account = account;
        this.channel = channel;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.status = VerificationStatus.PENDING;
        this.attemptCount = 0;
    }

    public static VerificationChallenge issue(Account account, VerificationChannel channel,
                                              VerificationPurpose purpose, String codeHash, Instant expiresAt) {
        return new VerificationChallenge(account, channel, purpose, codeHash, expiresAt);
    }

    public boolean isUsable(Instant now) {
        return this.status == VerificationStatus.PENDING && now.isBefore(this.expiresAt) && !isLocked();
    }

    public boolean isLocked() {
        return attemptCount >= MAX_ATTEMPTS;
    }

   public void consume(Instant now) {

       if (this.status != VerificationStatus.PENDING)
           throw new IllegalStateException("not consumable: " + status);

       if (!now.isBefore(expiresAt))
           throw new IllegalStateException("challenge expired");

       this.status = VerificationStatus.CONSUMED;
       this.consumedAt = now;
    }

    public void recordFailure(Instant now) {
        this.attemptCount++;
        this.lastAttemptAt = now;
    }

    public void supersede() {
        if (status == VerificationStatus.PENDING)
            this.status = VerificationStatus.SUPERSEDED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof VerificationChallenge other))
            return false;

        return id != null && id.equals(other.id);   // id 없으면 동일성(==)만 인정
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();   // 상수 — id 변해도 hashCode 불변 보장
    }

}
