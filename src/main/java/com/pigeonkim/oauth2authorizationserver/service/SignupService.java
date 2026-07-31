package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.Account;
import com.pigeonkim.oauth2authorizationserver.domain.Credential;
import com.pigeonkim.oauth2authorizationserver.domain.CredentialType;
import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;
import com.pigeonkim.oauth2authorizationserver.domain.VerificationPurpose;
import com.pigeonkim.oauth2authorizationserver.exception.DuplicateCredentialException;
import com.pigeonkim.oauth2authorizationserver.exception.VerificationFailedException;
import com.pigeonkim.oauth2authorizationserver.repository.AccountRepository;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * M4 컨트롤러① 지원 — 가입/이메일검증 오케스트레이션(얇은 응용 서비스).
 * 도메인 규칙은 엔티티/VerificationService에 있고, 여기선 "순서"만 엮는다.
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private final AccountRepository accountRepo;
    private final CredentialRepository credentialRepo;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;

    /**
     * 가입: Account + Credential(EMAIL_PASSWORD, 미검증) 생성 후 이메일 검증코드 발급.
     * (코드 발송은 이 트랜잭션 커밋 이후 — VerificationService의 AFTER_COMMIT 리스너)
     */
    @Transactional
    public void signup(String email, String rawPassword, String displayName) {
        // TODO:
        //  1) 중복 방어: credentialRepo.existsByEmailAndType(email, CredentialType.EMAIL_PASSWORD) 이면
        //               throw new IllegalStateException("already registered")
        //               (정식 예외타입 + 409 매핑은 8/4 'DTO+GlobalExceptionHandler' 슬롯에서)
        //  2) Account account = Account.of(displayName);  accountRepo.save(account);
        //  3) String passwordHash = passwordEncoder.encode(rawPassword);   // ★원문 절대 저장 금지
        //     Credential cred = Credential.basic(account, email, passwordHash);  credentialRepo.save(cred);
        //  4) verificationService.issue(account, VerificationChannel.EMAIL,
        //                               VerificationPurpose.SIGNUP_VERIFY, email);

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

    /**
     * 이메일 검증: email 로 account 를 찾아 코드 검증. 성공 시 credential.emailVerified=true.
     */
    @Transactional(noRollbackFor = VerificationFailedException.class)
    public void verifyEmail(String email, String code) {
        // TODO:
        //  1) credentialRepo.findByEmailAndType(email, CredentialType.EMAIL_PASSWORD)
        //       비어있으면 return false  (이메일 존재 여부를 노출하지 않으려 '실패'로 통일)
        //  2) Account account = credential.getAccount();
        //  3) return verificationService.verify(account, VerificationChannel.EMAIL,
        //                                        VerificationPurpose.SIGNUP_VERIFY, code);

        Optional<Credential> credential = credentialRepo.
                findByEmailAndType(email, CredentialType.EMAIL_PASSWORD);

        if (credential.isEmpty()) {
            throw new VerificationFailedException("verification failed");   // ★
        }

        Account account = credential.get().getAccount();
        boolean ok = verificationService.verify(account, VerificationChannel.EMAIL,
                VerificationPurpose.SIGNUP_VERIFY, code);

        if (!ok) {
            throw new VerificationFailedException("verification failed");   // ★ 위와 '똑같은 메시지'
        }
    }
}
