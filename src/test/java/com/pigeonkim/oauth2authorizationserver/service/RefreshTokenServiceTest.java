package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.RefreshToken;
import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamily;
import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamilyStatus;
import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenStatus;
import com.pigeonkim.oauth2authorizationserver.exception.RefreshTokenReuseException;
import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenFamilyRepository;
import com.pigeonkim.oauth2authorizationserver.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository tokenRepo;
    @Mock
    RefreshTokenFamilyRepository familyRepo;

    RefreshTokenService service;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);
        service = new RefreshTokenService(tokenRepo, familyRepo, clock);  // 진짜 Clock 주입
    }

    Instant exp() { return clock.instant().plusSeconds(3600); }

    // ───────── ★CRITICAL 1: CONSUMED 재제시 → 패밀리 전체 REVOKED (간판) ─────────
    @Test
    void reuse_of_consumed_token_revokes_whole_family() {
        // arrange: 회전이 한 번 일어난 패밀리 (jti1=CONSUMED, jti2=ACTIVE 현재토큰)
        RefreshTokenFamily family = RefreshTokenFamily.start(1L, "client", clock.instant());
        RefreshToken t1 = RefreshToken.issue(family, "jti1", clock.instant(), exp());
        t1.consume();                                          // 이미 소비됨(회전됨)
        RefreshToken t2 = RefreshToken.issue(family, "jti2", clock.instant(), exp());  // 현재 살아있는 토큰

        given(tokenRepo.findById("jti1")).willReturn(Optional.of(t1));
        given(tokenRepo.findByFamily(family)).willReturn(List.of(t1, t2));

        // act + assert: 이미 CONSUMED된 jti1을 또 제시 → 도난 예외
        assertThrows(RefreshTokenReuseException.class,
                () -> service.rotate("jti1", "jti3", exp()));

        // assert: 패밀리 + 체인 전부 무효 (현재 살아있던 t2까지!)
        assertThat(family.getStatus()).isEqualTo(RefreshTokenFamilyStatus.REVOKED);
        assertThat(t1.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
        assertThat(t2.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);   // ★도난 시 정상 토큰도 죽음 → 재로그인 강제
    }

    // ───────── ★CRITICAL 2: 정상 회전 ─────────
    @Test
    void normal_rotation_consumes_old_and_issues_new() {
        // TODO arrange: family + t1(ACTIVE)
        //   when(tokenRepo.findById("jti1")).thenReturn(Optional.of(t1));
        //   when(tokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));  // save는 받은 걸 그대로 반환
        // TODO act: RefreshToken next = service.rotate("jti1", "jti2", exp());
        // TODO assert: t1.getStatus()==CONSUMED, next.getStatus()==ACTIVE,
        //             next.getJti()=="jti2", next.getFamily()==family

        RefreshTokenFamily family = RefreshTokenFamily.start(1L, "client", clock.instant());
        RefreshToken t1 = RefreshToken.issue(family, "jti1", clock.instant(), exp());

        given(tokenRepo.findById("jti1")).willReturn(Optional.of(t1));
        given(tokenRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        RefreshToken next = service.rotate("jti1", "jti2", exp());

        assertThat(t1.getStatus()).isEqualTo(RefreshTokenStatus.CONSUMED);
        assertThat(next.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(next.getJti()).isEqualTo("jti2");
        assertThat(next.getFamily()).isEqualTo(family);
    }

    // ───────── ★CRITICAL 3: REVOKED 토큰 거부 ─────────
    @Test
    void revoked_token_is_rejected() {
        // TODO arrange: family + t1 후 t1.revoke() (REVOKED 상태로)
        //   when(tokenRepo.findById("jti1")).thenReturn(Optional.of(t1));
        // TODO act+assert: service.rotate("jti1","jti2",exp()) 가 IllegalArgumentException 던지는지

        RefreshTokenFamily family = RefreshTokenFamily.start(1L, "client", clock.instant());
        RefreshToken t1 = RefreshToken.issue(family, "jti1", clock.instant(), exp());
        t1.consume();
        t1.revoke();

        given(tokenRepo.findById("jti1")).willReturn(Optional.of(t1));

        assertThrows(IllegalArgumentException.class, () -> service.rotate("jti1","jti2", exp()));
    }
}