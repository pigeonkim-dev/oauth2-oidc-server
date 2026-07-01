package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.*;
import com.pigeonkim.oauth2authorizationserver.exception.LinkRefusedException;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock CredentialRepository credentialRepo;
    @Mock VerificationService verificationService;

    private LinkingService linkingService;

    @BeforeEach
    void setUp() {
        linkingService = new LinkingService(credentialRepo, verificationService);
    }

    @Test
    void initiateLink_성공() {
        // given
        Account account = mock(Account.class);
        Credential credential = mock(Credential.class);
        given(credentialRepo.findByEmailAndType("a@test.com", CredentialType.EMAIL_PASSWORD))
                .willReturn(Optional.of(credential));
        given(credential.isEmailVerified()).willReturn(true);
        given(credential.getAccount()).willReturn(account);

        // when
        linkingService.initiateLink("a@test.com");

        // then — 검증된 계정에 LINK 코드 발송이 호출됨
        verify(verificationService).issue(
                account, VerificationChannel.EMAIL, VerificationPurpose.LINK, "a@test.com");
    }

    @Test
    void initiateLink_미검증계정이면_거부() {   // ★CRITICAL pre-hijacking
        // given — 계정은 있지만 email_verified=false
        Credential credential = mock(Credential.class);
        given(credentialRepo.findByEmailAndType("a@test.com", CredentialType.EMAIL_PASSWORD))
                .willReturn(Optional.of(credential));
        given(credential.isEmailVerified()).willReturn(false);

        // when & then
        assertThrows(LinkRefusedException.class,
                () -> linkingService.initiateLink("a@test.com"));
        verify(verificationService, never()).issue(any(), any(), any(), any());
    }

    @Test
    void confirmLink_증명실패면_거부하고_저장안함() {   // ★CRITICAL 증명 없는 링크
        // given
        Account account = mock(Account.class);
        Credential credential = mock(Credential.class);
        given(credentialRepo.findByEmailAndType("a@test.com", CredentialType.EMAIL_PASSWORD))
                .willReturn(Optional.of(credential));
        given(credential.isEmailVerified()).willReturn(true);
        given(credential.getAccount()).willReturn(account);
        given(verificationService.verify(account, VerificationChannel.EMAIL, VerificationPurpose.LINK, "wrong"))
                .willReturn(false);
        PendingKakaoCredential proven = new PendingKakaoCredential("kakao", "uid", "k@test.com");

        // when & then
        assertThrows(LinkRefusedException.class,
                () -> linkingService.confirmLink("a@test.com", "wrong", proven));
        verify(credentialRepo, never()).save(any());
    }

    @Test
    void confirmLink_증명통과면_카카오credential_저장() {   // ★CRITICAL 정상 링크
        // given
        Account account = mock(Account.class);
        Credential credential = mock(Credential.class);
        given(credentialRepo.findByEmailAndType("a@test.com", CredentialType.EMAIL_PASSWORD))
                .willReturn(Optional.of(credential));
        given(credential.isEmailVerified()).willReturn(true);
        given(credential.getAccount()).willReturn(account);
        given(verificationService.verify(account, VerificationChannel.EMAIL, VerificationPurpose.LINK, "code123"))
                .willReturn(true);
        PendingKakaoCredential proven = new PendingKakaoCredential("kakao", "uid123", "k@test.com");

        // when
        linkingService.confirmLink("a@test.com", "code123", proven);

        // then — 기존 계정에 카카오 credential 저장
        verify(credentialRepo).save(any(Credential.class));
    }
}
