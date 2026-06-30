package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationChallengeRepository extends JpaRepository<VerificationChallenge, Long> {
    List<VerificationChallenge> findByAccountAndChannelAndPurposeAndStatus(
            Account account, VerificationChannel channel,
            VerificationPurpose purpose, VerificationStatus status);

    List<VerificationChallenge> findByAccountAndChannelAndPurposeAndStatusOrderByCreatedAtDesc(
            Account account, VerificationChannel channel, VerificationPurpose purpose, VerificationStatus status);
}
