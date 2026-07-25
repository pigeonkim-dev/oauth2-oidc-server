package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.JpaOAuth2Authorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JpaOAuth2AuthorizationRepository
        extends JpaRepository<JpaOAuth2Authorization, String> {

    Optional<JpaOAuth2Authorization> findByState(String state);
    Optional<JpaOAuth2Authorization> findByAuthorizationCodeValue(String code);
    Optional<JpaOAuth2Authorization> findByAccessTokenValue(String token);
    Optional<JpaOAuth2Authorization> findByRefreshTokenValue(String token);
    Optional<JpaOAuth2Authorization> findByOidcIdTokenValue(String token);

    // 타입 모를 때: 아무 컬럼에서나 매칭 (JPQL 직접 작성)
    @Query("select a from JpaOAuth2Authorization a " +
            "where a.state = :t or a.authorizationCodeValue = :t " +
            "or a.accessTokenValue = :t or a.refreshTokenValue = :t " +
            "or a.oidcIdTokenValue = :t")
    Optional<JpaOAuth2Authorization> findByAnyToken(@Param("t") String token);
}
