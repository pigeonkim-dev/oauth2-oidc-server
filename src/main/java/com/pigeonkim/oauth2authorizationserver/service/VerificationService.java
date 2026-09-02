package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.*;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;
import com.pigeonkim.oauth2authorizationserver.repository.VerificationChallengeRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);
    private static final int CODE_DIGITS = 6;
    private final VerificationChallengeRepository challengeRepo;
    private final CredentialRepository credentialRepo;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    private static final int CODE_LENGTH = 8;

    @Transactional
    public void issue(Account account, VerificationChannel channel, VerificationPurpose purpose, String destination) {

        challengeRepo.findByAccountAndChannelAndPurposeAndStatus(account, channel, purpose, VerificationStatus.PENDING)
                .forEach(VerificationChallenge::supersede);

        String rawCode = generateCode();

        VerificationChallenge challenge = VerificationChallenge.issue(
                account, channel, purpose,
                passwordEncoder.encode(rawCode), now().plus(TTL));

        challengeRepo.save(challenge);

        eventPublisher.publishEvent(new VerificationIssuedEvent(
                challenge.getId(), channel, destination, rawCode));
    }

    @Transactional
    public boolean verify(Account account, VerificationChannel channel, VerificationPurpose purpose, String rawCode) {

        List<VerificationChallenge> pending = challengeRepo.findByAccountAndChannelAndPurposeAndStatusOrderByCreatedAtDesc(
                account, channel, purpose, VerificationStatus.PENDING);

        if (pending.isEmpty())
            return false;

        VerificationChallenge latest = pending.get(0);

        pending.stream()
                .skip(1)
                .forEach(VerificationChallenge::supersede);

        Instant now = now();

        if (latest.isLocked()) {
            latest.supersede();
            return false;
        }
        if (!latest.isUsable(now))
            return false;

        if (latest.getLastAttemptAt() != null
                && Duration.between(latest.getLastAttemptAt(), now).compareTo(MIN_INTERVAL) < 0)
            return false;

        if (!passwordEncoder.matches(rawCode, latest.getCodeHash())) {
            latest.recordFailure(now);
            return false;
        }

        latest.consume(now);

        if (purpose == VerificationPurpose.SIGNUP_VERIFY) {
            credentialRepo.findByAccountAndType(account, CredentialType.EMAIL_PASSWORD)
                    .ifPresent(Credential::markEmailVerified);
        }

        return true;
    }

    private Instant now() {
        return clock.instant();
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);   // 모듈로 편향 없음
        }
        return sb.toString();
    }
}
