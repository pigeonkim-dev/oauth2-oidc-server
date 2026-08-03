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

@Service
@RequiredArgsConstructor
public class SignupService {

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
    }
}
