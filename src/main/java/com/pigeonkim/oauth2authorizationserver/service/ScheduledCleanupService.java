package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenFamilyRepository;
import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenRepository;
import com.pigeonkim.oauth2authorizationserver.repository.VerificationChallengeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledCleanupService {

    private final VerificationChallengeRepository challengeRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final RefreshTokenFamilyRepository familyRepo;
    private final Clock clock;

    @Value("${app.cleanup.retention-days:7}")            // 설정 없으면 기본 7일
    private long retentionDays;

    @Scheduled(cron = "${app.cleanup.cron:0 0 3 * * *}") // 설정 없으면 기본: 매일 새벽 3시
    @Transactional
    public void cleanup() {

        Instant cutOff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        int challenges = challengeRepo.deleteExpiredBefore(cutOff);
        int tokens = refreshTokenRepo.deleteExpiredBefore(cutOff);
        int families = familyRepo.deleteEmptyFamilies();

        log.info("[cleanup] challenges={}, tokens={},families={}", challenges, tokens, families);
    }
}
