package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.JpaOAuth2Authorization;
import com.pigeonkim.oauth2authorizationserver.repository.JpaOAuth2AuthorizationRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final JpaOAuth2AuthorizationRepository repository;
    private final RegisteredClientRepository registeredClientRepository;
    private final JsonMapper jsonMapper;   // tools.jackson.databind.json.JsonMapper (Jackson 3!)

    public JpaOAuth2AuthorizationService(JpaOAuth2AuthorizationRepository repository,
                                         RegisteredClientRepository registeredClientRepository) {
        this.repository = repository;
        this.registeredClientRepository = registeredClientRepository;
        // ★ 함정 방지: 평범한 ObjectMapper 쓰면 SAS 보안타입 직렬화가 터짐.
        //   SAS가 쓰는 것과 동일한 보안 모듈 등록.
        List<JacksonModule> modules =
                SecurityJacksonModules.getModules(getClass().getClassLoader());
        this.jsonMapper = JsonMapper.builder().addModules(modules).build();
    }

    // ───────── 인터페이스 4메서드 ─────────
    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        repository.save(toEntity(authorization));   // save = insert or update (같은 PK면 갱신)
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        repository.deleteById(authorization.getId());
    }

    @Override
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return repository.findById(id).map(this::toObject).orElse(null);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        Optional<JpaOAuth2Authorization> result;
        if (tokenType == null) {
            result = repository.findByAnyToken(token);
        } else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            result = repository.findByState(token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            result = repository.findByAuthorizationCodeValue(token);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            result = repository.findByAccessTokenValue(token);
        } else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            result = repository.findByOidcIdTokenValue(token);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            result = repository.findByRefreshTokenValue(token);
        } else {
            result = Optional.empty();
        }
        return result.map(this::toObject).orElse(null);
    }

    // ───────── 변환: 쓰기 (SAS객체 → 엔티티) ─────────
    private JpaOAuth2Authorization toEntity(OAuth2Authorization a) {
        JpaOAuth2Authorization e = new JpaOAuth2Authorization();
        e.setId(a.getId());
        e.setRegisteredClientId(a.getRegisteredClientId());
        e.setPrincipalName(a.getPrincipalName());
        e.setAuthorizationGrantType(a.getAuthorizationGrantType().getValue());
        e.setAuthorizedScopes(StringUtils.collectionToDelimitedString(a.getAuthorizedScopes(), ","));
        e.setAttributes(writeMap(a.getAttributes()));
        e.setState(a.getAttribute(OAuth2ParameterNames.STATE));   // state는 attributes 안에 들어있음

        // ── 패턴 예시: authorization_code 블록 (이건 제가 채움) ──
        OAuth2Authorization.Token<OAuth2AuthorizationCode> code = a.getToken(OAuth2AuthorizationCode.class);
        if (code != null) {
            e.setAuthorizationCodeValue(code.getToken().getTokenValue());
            e.setAuthorizationCodeIssuedAt(code.getToken().getIssuedAt());
            e.setAuthorizationCodeExpiresAt(code.getToken().getExpiresAt());
            e.setAuthorizationCodeMetadata(writeMap(code.getMetadata()));
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = a.getToken(OAuth2AccessToken.class);
        if (accessToken != null) {
            e.setAccessTokenType(accessToken.getToken().getTokenType().getValue());

            if (!accessToken.getToken().getScopes().isEmpty()) {
                e.setAccessTokenScopes(
                        StringUtils.collectionToDelimitedString(accessToken.getToken().getScopes(), ","));
            }

            e.setAccessTokenValue(accessToken.getToken().getTokenValue());
            e.setAccessTokenIssuedAt(accessToken.getToken().getIssuedAt());
            e.setAccessTokenExpiresAt(accessToken.getToken().getExpiresAt());
            e.setAccessTokenMetadata(writeMap(accessToken.getMetadata()));
        }

       OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = a.getRefreshToken();
        if (refreshToken != null){
            e.setRefreshTokenValue(refreshToken.getToken().getTokenValue());
            e.setRefreshTokenIssuedAt(refreshToken.getToken().getIssuedAt());
            e.setRefreshTokenExpiresAt(refreshToken.getToken().getExpiresAt());
            e.setRefreshTokenMetadata(writeMap(refreshToken.getMetadata()));
        }

        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = a.getToken(OidcIdToken.class);
        if (oidcIdToken != null) {
            e.setOidcIdTokenValue(oidcIdToken.getToken().getTokenValue());
            e.setOidcIdTokenIssuedAt(oidcIdToken.getToken().getIssuedAt());
            e.setOidcIdTokenExpiresAt(oidcIdToken.getToken().getExpiresAt());
            e.setOidcIdTokenMetadata(writeMap(oidcIdToken.getMetadata()));
        }

        return e;
    }

    // ───────── 변환: 읽기 (엔티티 → SAS객체) ─────────
    private OAuth2Authorization toObject(JpaOAuth2Authorization  jpaOAuth2Authorization) {

        RegisteredClient rc = registeredClientRepository.findById(jpaOAuth2Authorization.getRegisteredClientId());
        if (rc == null) {
            throw new DataRetrievalFailureException(
                    "RegisteredClient '" + jpaOAuth2Authorization.getRegisteredClientId() + "' not found");
        }

        OAuth2Authorization.Builder oatuh2AuthorizationBuilder = OAuth2Authorization.withRegisteredClient(rc)
                .id(jpaOAuth2Authorization.getId())
                .principalName(jpaOAuth2Authorization.getPrincipalName())
                .authorizationGrantType(new AuthorizationGrantType(jpaOAuth2Authorization.getAuthorizationGrantType()))
                .authorizedScopes(StringUtils.commaDelimitedListToSet(jpaOAuth2Authorization.getAuthorizedScopes()))
                .attributes(attrs -> attrs.putAll(parseMap(jpaOAuth2Authorization.getAttributes())));

        if (StringUtils.hasText(jpaOAuth2Authorization.getState())) {
            oatuh2AuthorizationBuilder.attribute(OAuth2ParameterNames.STATE, jpaOAuth2Authorization.getState());
        }

        // ── 패턴 예시: authorization_code 블록 (제가 채움) ──
        if (StringUtils.hasText(jpaOAuth2Authorization.getAuthorizationCodeValue())) {
            OAuth2AuthorizationCode code = new OAuth2AuthorizationCode(
                    jpaOAuth2Authorization.getAuthorizationCodeValue(),
                    jpaOAuth2Authorization.getAuthorizationCodeIssuedAt(),
                    jpaOAuth2Authorization.getAuthorizationCodeExpiresAt());
            oatuh2AuthorizationBuilder.token(code,
                    md -> md.putAll(parseMap(jpaOAuth2Authorization.getAuthorizationCodeMetadata())));
        }

        if (StringUtils.hasText(jpaOAuth2Authorization.getAccessTokenValue())) {
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    jpaOAuth2Authorization.getAccessTokenValue(),
                    jpaOAuth2Authorization.getAccessTokenIssuedAt(),
                    jpaOAuth2Authorization.getAccessTokenExpiresAt(),
                    StringUtils.commaDelimitedListToSet(jpaOAuth2Authorization.getAccessTokenScopes())
                    );

            oatuh2AuthorizationBuilder.token(accessToken,
                    md -> md.putAll(parseMap(jpaOAuth2Authorization.getAccessTokenMetadata())));
        }

        if (StringUtils.hasText(jpaOAuth2Authorization.getRefreshTokenValue())) {
            OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                    jpaOAuth2Authorization.getRefreshTokenValue(),
                    jpaOAuth2Authorization.getRefreshTokenIssuedAt(),
                    jpaOAuth2Authorization.getAccessTokenExpiresAt());

            oatuh2AuthorizationBuilder.token(refreshToken,
                    md -> md.putAll(parseMap(jpaOAuth2Authorization.getRefreshTokenMetadata())));
        }

        if (StringUtils.hasText(jpaOAuth2Authorization.getOidcIdTokenValue())) {
            Map md = parseMap(jpaOAuth2Authorization.getOidcIdTokenMetadata());
            Map claims = (Map) md.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);

            OidcIdToken oidcIdToken = new OidcIdToken(
                    jpaOAuth2Authorization.getOidcIdTokenValue(),
                    jpaOAuth2Authorization.getRefreshTokenIssuedAt(),
                    jpaOAuth2Authorization.getRefreshTokenExpiresAt(),
                    claims);

            oatuh2AuthorizationBuilder.token(oidcIdToken,
                    m -> md.putAll(md));
        }

        return oatuh2AuthorizationBuilder.build();
    }

    // ───────── JSON 헬퍼 (제가 채움) ─────────
    private String writeMap(Map<String, Object> data) {
        try { return jsonMapper.writeValueAsString(data); }
        catch (Exception ex) { throw new IllegalArgumentException(ex.getMessage(), ex); }
    }

    private Map<String, Object> parseMap(String data) {
        if (!StringUtils.hasText(data)) return Collections.emptyMap();
        try {
            var typeRef = new ParameterizedTypeReference<Map<String, Object>>() {};
            return jsonMapper.readValue(data, jsonMapper.getTypeFactory().constructType(typeRef.getType()));
        } catch (Exception ex) { throw new IllegalArgumentException(ex.getMessage(), ex); }
    }
}