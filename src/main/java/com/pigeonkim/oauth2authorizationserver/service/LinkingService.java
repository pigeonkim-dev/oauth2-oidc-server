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
        // 1) existingEmail 의 EMAIL_PASSWORD credential 찾기
        //    - 없거나 isEmailVerified()==false 면 throw new LinkRefusedException(...)
        // 2) verificationService.issue(account, EMAIL, LINK, existingEmail)

        Credential credential = credentialRepo.findByEmailAndType(existingEmail, CredentialType.EMAIL_PASSWORD)
                .filter(Credential::isEmailVerified)
                .orElseThrow(() -> new LinkRefusedException("dose not verified"));

        verificationService.issue(credential.getAccount(),
                VerificationChannel.EMAIL, VerificationPurpose.LINK, existingEmail);

    }

    @Transactional
    public void confirmLink(String existingEmail, String linkCode, PendingKakaoCredential proven) {
        // 1) existingEmail 의 검증된 EMAIL_PASSWORD credential 찾기 → account 꺼내기
        //    (없거나 미검증이면 LinkRefusedException)
        // 2) verificationService.verify(account, EMAIL, LINK, linkCode)
        //    - false 면 throw new LinkRefusedException(...)
        // 3) [D3] 통과 시에만: credentialRepo.save(Credential.kakao(account, proven...))

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
                Credential.kakao(credential.getAccount(), proven.email(),
                        proven.provider(), proven.providerUid()));


    }
}