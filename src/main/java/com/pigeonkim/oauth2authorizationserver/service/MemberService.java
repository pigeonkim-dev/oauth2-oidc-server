package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.Account;
import com.pigeonkim.oauth2authorizationserver.domain.Credential;
import com.pigeonkim.oauth2authorizationserver.domain.CredentialType;
import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;
import com.pigeonkim.oauth2authorizationserver.domain.VerificationPurpose;
import com.pigeonkim.oauth2authorizationserver.exception.AccountNotFoundException;
import com.pigeonkim.oauth2authorizationserver.exception.DuplicateCredentialException;
import com.pigeonkim.oauth2authorizationserver.exception.VerificationFailedException;
import com.pigeonkim.oauth2authorizationserver.repository.AccountRepository;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;

import com.pigeonkim.oauth2authorizationserver.web.dto.MeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 회원(Account+Credential 애그리거트) 라이프사이클 애플리케이션 서비스.
 * <p>
 * 회원 유스케이스를 담는다 — 지금은 가입(signup)/이메일 검증(verifyEmail), 이후 P3 에서 수정/탈퇴 추가.
 * 한 동작이 Account 와 Credential 두 엔티티를 함께 다루므로 엔티티별 서비스가 아니라 이 애그리거트 서비스에 모은다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final AccountRepository accountRepo;
    private final CredentialRepository credentialRepo;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;

    @Transactional
    public void signup(String email, String rawPassword, String displayName) {

        if (credentialRepo
                .existsByEmailAndType(email, CredentialType.EMAIL_PASSWORD)) {

            throw new DuplicateCredentialException("already registered");
        }

        Account account = Account.of(displayName);
        accountRepo.save(account);

        String passwordHash = passwordEncoder.encode(rawPassword);

        Credential credential = Credential.basic(account, email, passwordHash);

        credentialRepo.save(credential);

        verificationService.issue(account, VerificationChannel.EMAIL,
                VerificationPurpose.SIGNUP_VERIFY, email);
    }

    @Transactional(noRollbackFor = VerificationFailedException.class)
    public void verifyEmail(String email, String code) {

        Optional<Credential> credential = credentialRepo.
                findByEmailAndType(email, CredentialType.EMAIL_PASSWORD);

        if (credential.isEmpty()) {
            throw new VerificationFailedException("verification failed");
        }

        Account account = credential.get().getAccount();
        boolean ok = verificationService.verify(account, VerificationChannel.EMAIL,
                VerificationPurpose.SIGNUP_VERIFY, code);

        if (!ok) {
            throw new VerificationFailedException("verification failed");
        }

        account.activate();
    }

    @Transactional(readOnly = true)
    public MeDto me(String email) {

        Optional<Credential> credential = credentialRepo
                .findWithAccountByEmailAndType(email, CredentialType.EMAIL_PASSWORD);

        if (credential.isEmpty()) {
            throw new AccountNotFoundException("처리 할 수 없습니다.");
        }

        return new MeDto(
                credential.get().getEmail(),
                credential.get().getAccount().getDisplayName(),
                credential.get().getAccount().getPhoneNumber());
    }
}
