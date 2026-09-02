package com.pigeonkim.oauth2authorizationserver.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel.EMAIL;
import static com.pigeonkim.oauth2authorizationserver.domain.VerificationPurpose.SIGNUP_VERIFY;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class VerificationChallengeTest {     // 애너테이션 없음 = 순수 단위

    // 고정 시간축 — 테스트가 실제 시계에 의존하지 않게
    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final Instant EXPIRES = NOW.plus(10, ChronoUnit.MINUTES);

    // 공통 given: PENDING 챌린지 하나
    private VerificationChallenge pending() {
        return VerificationChallenge.issue(mock(Account.class), EMAIL, SIGNUP_VERIFY, "hash", EXPIRES);
    }

    @Test
    void consume_정상_PENDING이면_CONSUMED로_전이하고_consumedAt기록() {
        // given
        VerificationChallenge c = pending();
        // when
        c.consume(NOW);
        // then
        assertThat(c.getStatus()).isEqualTo(VerificationStatus.CONSUMED);
        assertThat(c.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void consume_이미소비된코드면_예외() {
        // given
        VerificationChallenge c = pending();
        c.consume(NOW);                       // 한 번 소비

        // when / then
        assertThatThrownBy(() -> c.consume(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consume_만료되었으면_예외() {
        // given
        VerificationChallenge c = pending();
        Instant afterExpiry = EXPIRES.plusSeconds(1);
        // when / then
        assertThatThrownBy(() -> c.consume(afterExpiry))                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isLocked_실패5회누적되면_true() {
        // given
        VerificationChallenge c = pending();
        // when
        for (int i = 0; i < 5; i++) c.recordFailure(NOW);
        // then
        assertThat(c.isLocked()).isTrue();
        assertThat(c.getAttemptCount()).isEqualTo(5);
    }

    @Test
    void supersede_PENDING만_무효화하고_CONSUMED는_불변() {
        // given
        VerificationChallenge c = pending();
        c.consume(NOW);                       // CONSUMED 상태로
        // when
        c.supersede();
        // then
        assertThat(c.getStatus()).isEqualTo(VerificationStatus.CONSUMED); // 안 바뀜
    }

    @Test
    void isUsable_PENDING이고_미만료이고_미잠금이면_true() {
        assertThat(pending().isUsable(NOW)).isTrue();
    }

    @Test
    void recordFailure_시도횟수증가_시각기록() {
        // given
        VerificationChallenge c = pending();
        // when
        c.recordFailure(NOW);
        // then
        assertEquals(1, c.getAttemptCount());      // 0 → 1
        assertEquals(NOW, c.getLastAttemptAt());   // 시각 기록됨
    }

    @Test
    void supersede_PENDING이면_SUPERSEDED로() {
        // given
        VerificationChallenge c = pending();
        // when
        c.supersede();
        // then
        assertEquals(VerificationStatus.SUPERSEDED, c.getStatus());
    }

    @Test
    void isUsable_만료되면_false() {
        // given
        VerificationChallenge c = pending();
        // when & then  (만료시각 이후를 now로)
        assertFalse(c.isUsable(EXPIRES.plusSeconds(1)));
    }

    @Test
    void isUsable_5회잠기면_false() {
        // given
        VerificationChallenge c = pending();
        for (int i = 0; i < 5; i++) c.recordFailure(NOW);
        // when & then
        assertFalse(c.isUsable(NOW));   // 안 만료됐어도 잠기면 못 씀
    }

    @Test
    void isUsable_소비되면_false() {
        // given
        VerificationChallenge c = pending();
        c.consume(NOW);
        // when & then
        assertFalse(c.isUsable(NOW));   // CONSUMED는 PENDING 아니므로 false
    }
}