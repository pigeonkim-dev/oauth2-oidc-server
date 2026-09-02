package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenFamilyRepository;
import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenRepository;
import com.pigeonkim.oauth2authorizationserver.repository.VerificationChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledCleanupServiceTest {

    @Mock
    VerificationChallengeRepository challengeRepo;
    @Mock
    RefreshTokenRepository refreshTokenRepo;
    @Mock
    RefreshTokenFamilyRepository familyRepo;

    private ScheduledCleanupService service;

    // 고정 시각 — Clock.fixed 로 결정론 확보 (Instant.now() 안 씀)
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final long RETENTION_DAYS = 7L;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        // @RequiredArgsConstructor 가 만든 4-인자 생성자 (retentionDays 는 @Value 필드라 제외)
        service = new ScheduledCleanupService(challengeRepo, refreshTokenRepo, familyRepo, fixedClock);
        // @Value 필드는 생성자에 없으니 리플렉션으로 주입 (스프링 컨텍스트 없이 테스트하려고)
        ReflectionTestUtils.setField(service, "retentionDays", RETENTION_DAYS);
    }

    @Test
    void cleanup_컷오프는_현재시각_빼기_보존기간() {   // ← 정답 예시
        // when
        service.cleanup();

        // then — cutoff = NOW - 7일 로 각 삭제 쿼리가 호출됨
        Instant expectedCutoff = NOW.minus(RETENTION_DAYS, ChronoUnit.DAYS);
        verify(challengeRepo).deleteExpiredBefore(expectedCutoff);
        verify(refreshTokenRepo).deleteExpiredBefore(expectedCutoff);
        verify(familyRepo).deleteEmptyFamilies();
    }

    @Test
    void cleanup_토큰을_패밀리보다_먼저_삭제() {

        InOrder order = inOrder(challengeRepo, refreshTokenRepo, familyRepo);

        service.cleanup();

        Instant expectedCutoff = NOW.minus(RETENTION_DAYS, ChronoUnit.DAYS);

        order.verify(challengeRepo).deleteExpiredBefore(expectedCutoff);
        order.verify(refreshTokenRepo).deleteExpiredBefore(any());
        order.verify(familyRepo).deleteEmptyFamilies();
    }
}
