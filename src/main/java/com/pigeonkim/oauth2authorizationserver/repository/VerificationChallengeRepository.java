package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VerificationChallengeRepository extends JpaRepository<VerificationChallenge, Long> {
    List<VerificationChallenge> findByAccountAndChannelAndPurposeAndStatus(
            Account account, VerificationChannel channel,
            VerificationPurpose purpose, VerificationStatus status);

    List<VerificationChallenge> findByAccountAndChannelAndPurposeAndStatusOrderByCreatedAtDesc(
            Account account, VerificationChannel channel, VerificationPurpose purpose, VerificationStatus status);

    @Modifying
    @Query("delete from VerificationChallenge c where c.expiresAt < :cutoff")   // ← 직접
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
