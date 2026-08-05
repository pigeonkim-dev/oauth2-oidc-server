package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.Account;
import com.pigeonkim.oauth2authorizationserver.domain.Credential;
import com.pigeonkim.oauth2authorizationserver.domain.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<Credential, Long> {
    Optional<Credential> findByEmailAndType(String email, CredentialType type);

    Optional<Credential> findByProviderAndProviderUid(String provider, String providerUid);

    boolean existsByEmailAndType(String email, CredentialType type);

    Optional<Credential> findByAccountAndType(Account account, CredentialType type);

    @Query("select c from Credential c join fetch c.account where c.email = :email and c.type = :type")
    Optional<Credential> findWithAccountByEmailAndType(@Param("email") String email, @Param("type") CredentialType type);
}
