package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.*;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;
import com.pigeonkim.oauth2authorizationserver.repository.VerificationChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel.EMAIL;
import static com.pigeonkim.oauth2authorizationserver.domain.VerificationPurpose.SIGNUP_VERIFY;
import static com.pigeonkim.oauth2authorizationserver.domain.VerificationStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock VerificationChallengeRepository challengeRepo;
    @Mock CredentialRepository credentialRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;

    // Clock 은 mock 대신 '고정 시계'를 직접 주입 → 시간이 결정론적
    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private VerificationService service;

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor 가 만든 생성자(필드 선언 순서)로 직접 조립
        service = new VerificationService(challengeRepo, credentialRepo, passwordEncoder, clock, eventPublisher);
    }

    private VerificationChallenge pendingChallenge(Account account) {
        // 만료 10분 뒤, PENDING, codeHash="hash"
        return VerificationChallenge.issue(account, EMAIL, SIGNUP_VERIFY, "hash", NOW.plusSeconds(600));
    }

    @Test
    void issue_기존PENDING폐기_저장_이벤트발행() {
        // given
        Account account = mock(Account.class);
        VerificationChallenge old = mock(VerificationChallenge.class);
        given(challengeRepo.findByAccountAndChannelAndPurposeAndStatus(account, EMAIL, SIGNUP_VERIFY, PENDING))
                .willReturn(List.of(old));
        given(passwordEncoder.encode(anyString())).willReturn("hash");

        // when
        service.issue(account, EMAIL, SIGNUP_VERIFY, "a@a.com");

        // then
        verify(old).supersede();                                       // 정책1
        verify(challengeRepo).save(any(VerificationChallenge.class));   // 저장됨
        verify(eventPublisher).publishEvent(any(VerificationIssuedEvent.class)); // 발송 이벤트
    }

    @Test
    void verify_성공이면_consume하고_emailVerified반영() {
        // given
        Account account = mock(Account.class);
        VerificationChallenge ch = pendingChallenge(account);
        given(challengeRepo.findByAccountAndChannelAndPurposeAndStatusOrderByCreatedAtDesc(account, EMAIL, SIGNUP_VERIFY, PENDING))
                .willReturn(List.of(ch));
        given(passwordEncoder.matches("123456", "hash")).willReturn(true);
        Credential cred = mock(Credential.class);
        given(credentialRepo.findByAccountAndType(account, CredentialType.EMAIL_PASSWORD))
                .willReturn(Optional.of(cred));

        // when
        boolean result = service.verify(account, EMAIL, SIGNUP_VERIFY, "123456");

        // then
        assertThat(result).isTrue();
        assertThat(ch.getStatus()).isEqualTo(VerificationStatus.CONSUMED);
        verify(cred).markEmailVerified();
    }

    @Test
    void verify_틀린코드면_실패하고_시도횟수증가() {
        // given
        Account account = mock(Account.class);
        VerificationChallenge ch = pendingChallenge(account);
        given(challengeRepo.findByAccountAndChannelAndPurposeAndStatusOrderByCreatedAtDesc(account, EMAIL, SIGNUP_VERIFY, PENDING))
                .willReturn(List.of(ch));
        given(passwordEncoder.matches("000000", "hash")).willReturn(false);

        // when
        boolean result = service.verify(account, EMAIL, SIGNUP_VERIFY, "000000");

        // then
        assertThat(result).isFalse();
        assertThat(ch.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void verify_PENDING이없으면_false() {
        // given
        Account account = mock(Account.class);
        given(challengeRepo.findByAccountAndChannelAndPurposeAndStatusOrderByCreatedAtDesc(account, EMAIL, SIGNUP_VERIFY, PENDING))
                .willReturn(List.of());

        // when & then
        assertThat(service.verify(account, EMAIL, SIGNUP_VERIFY, "x")).isFalse();
    }
}
