package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.*;
import com.pigeonkim.oauth2authorizationserver.exception.LinkRefusedException;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkingService {

    private final CredentialRepository credentialRepo;
    private final VerificationService verificationService;

    @Transactional
    public void initiateLink(String existingEmail) {

        Credential credential = credentialRepo.findByEmailAndType(existingEmail, CredentialType.EMAIL_PASSWORD)
                .filter(Credential::isEmailVerified)
                .orElseThrow(() -> new LinkRefusedException("dose not verified"));

        verificationService.issue(credential.getAccount(),
                VerificationChannel.EMAIL, VerificationPurpose.LINK, existingEmail);

    }

    @Transactional(noRollbackFor = LinkRefusedException.class)
    public void confirmLink(String existingEmail, String linkCode, PendingKakaoCredential proven) {

        Credential credential = credentialRepo.findByEmailAndType(
                        existingEmail, CredentialType.EMAIL_PASSWORD)
                .filter(Credential::isEmailVerified)
                .orElseThrow(() -> new LinkRefusedException("not verified"));

        boolean isVerified = verificationService.verify(credential.getAccount(),
                VerificationChannel.EMAIL, VerificationPurpose.LINK, linkCode);

        if (!isVerified) {
            throw new LinkRefusedException("link proof failed");
        }

        credentialRepo.save(
                Credential.oauth(credential.getAccount(), proven.email(),
                        proven.provider(), proven.providerUid()));


    }
}