package com.pigeonkim.oauth2authorizationserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @Column(length = 100) private String registeredClientId;
    @Column(length = 200) private String principalName;
    @Column(length = 100) private String authorizationGrantType;
    @Column(length = 1000) private String authorizedScopes;

    @Column(columnDefinition = "text") private String attributes;   // 큰 JSON → text
    @Column(length = 500) private String state;

    // ── authorization_code 블록 (패턴 예시) ──
    @Column(columnDefinition = "text") private String authorizationCodeValue;
    private Instant authorizationCodeIssuedAt;
    private Instant authorizationCodeExpiresAt;
    @Column(columnDefinition = "text") private String authorizationCodeMetadata;

    // ── TODO: access_token 블록 ──
    //   accessTokenValue(text), accessTokenIssuedAt, accessTokenExpiresAt,
    //   accessTokenMetadata(text), accessTokenType(varchar 100), accessTokenScopes(varchar 1000)

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

    // ── TODO: refresh_token 블록 ──
    //   refreshTokenValue(text), refreshTokenIssuedAt, refreshTokenExpiresAt, refreshTokenMetadata(text)

    @Column(columnDefinition = "text")
    private String refreshTokenValue;
    private Instant refreshTokenIssuedAt;
    private Instant refreshTokenExpiresAt;

    @Column(columnDefinition = "text")
    private String refreshTokenMetadata;

    // ── TODO: oidc_id_token 블록 ──
    //   oidcIdTokenValue(text), oidcIdTokenIssuedAt, oidcIdTokenExpiresAt, oidcIdTokenMetadata(text)

    @Column(columnDefinition = "text")
    private String oidcIdTokenValue;
    private Instant oidcIdTokenIssuedAt;
    private Instant oidcIdTokenExpiresAt;

    @Column(columnDefinition = "text")
    private String oidcIdTokenMetadata;

    public JpaOAuth2Authorization() { }    // JPA 기본 생성자

    // TODO: getter/setter — 변환 로직에서 많이 쓰니 다 필요.
    //       Lombok @Getter @Setter 붙여도 됨(T1 equals/hashCode 함정만 조심 → 여기선 안 씀)
}