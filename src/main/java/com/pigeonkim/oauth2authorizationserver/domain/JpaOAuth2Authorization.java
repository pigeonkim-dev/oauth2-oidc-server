package com.pigeonkim.oauth2authorizationserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "jpa_oauth2_authorization")
@Getter
@Setter
public class JpaOAuth2Authorization {

    @Id
    @Column(length = 100)
    private String id;

    @Column(length = 100)
    private String registeredClientId;

    @Column(length = 200)
    private String principalName;

    @Column(length = 100)
    private String authorizationGrantType;

    @Column(length = 1000)
    private String authorizedScopes;

    @Column(columnDefinition = "text")
    private String attributes;

    @Column(length = 500)
    private String state;

    @Column(columnDefinition = "text")
    private String authorizationCodeValue;

    private Instant authorizationCodeIssuedAt;

    private Instant authorizationCodeExpiresAt;

    @Column(columnDefinition = "text")
    private String authorizationCodeMetadata;

    @Column(columnDefinition = "text")
    private String accessTokenValue;

    private Instant accessTokenIssuedAt;

    private Instant accessTokenExpiresAt;

    @Column(columnDefinition = "text")
    private String accessTokenMetadata;

    @Column(length = 100)
    private String accessTokenType;

    @Column(length = 1000)
    private String accessTokenScopes;

    @Column(columnDefinition = "text")
    private String refreshTokenValue;

    private Instant refreshTokenIssuedAt;

    private Instant refreshTokenExpiresAt;

    @Column(columnDefinition = "text")
    private String refreshTokenMetadata;

    @Column(columnDefinition = "text")
    private String oidcIdTokenValue;

    private Instant oidcIdTokenIssuedAt;

    private Instant oidcIdTokenExpiresAt;

    @Column(columnDefinition = "text")
    private String oidcIdTokenMetadata;

    public JpaOAuth2Authorization() {
    }
}