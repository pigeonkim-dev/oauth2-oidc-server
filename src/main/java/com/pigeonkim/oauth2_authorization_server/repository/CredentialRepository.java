package com.pigeonkim.oauth2_authorization_server.repository;

import com.pigeonkim.oauth2_authorization_server.domain.Credential;
import com.pigeonkim.oauth2_authorization_server.domain.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<Credential, Long> {
    Optional<Credential> findByEmailAndType(String email, CredentialType type);

    Optional<Credential> findByProviderAndProviderUid(String provider, String providerUid);

    boolean existsByEmailAndType(String email, CredentialType type);
}
