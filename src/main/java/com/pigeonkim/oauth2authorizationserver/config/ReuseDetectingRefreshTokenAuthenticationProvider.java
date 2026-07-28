package com.pigeonkim.oauth2authorizationserver.config;

import com.pigeonkim.oauth2authorizationserver.exception.RefreshTokenReuseException;
import com.pigeonkim.oauth2authorizationserver.service.RefreshTokenService;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

/**
 * T4 배선 — SAS의 refresh grant 처리기(OAuth2RefreshTokenAuthenticationProvider)를 "감싸서"
 * 재사용 탐지 + 우리 대장부(refresh_token/family) 갱신을 끼워넣는 래퍼.
 * <p>
 * SAS의 원본 provider는 final 이라 상속 불가 → 이렇게 delegate(위임)로 감싼다.
 * <p>
 * 흐름:
 * BEFORE : 우리 대장부에서 presentedValue 조회 → 이미 CONSUMED면 🚨재사용 → family revoke + invalid_grant
 * DELEGATE: 진짜 SAS provider가 회전(옛것 교체) + 새 refresh 발급
 * AFTER  : 결과에서 새 refresh 값을 꺼내 rotate(presentedValue, newValue, ...)로 대장부 갱신
 * <p>
 * ※ jti(PK)는 SAS 기본 refresh가 불투명 문자열이므로 "refresh 토큰 값 그 자체"를 쓴다.
 */
public class ReuseDetectingRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;          // SAS 원본 refresh provider
    private final RefreshTokenService refreshTokenService;  // 우리 대장부

    public ReuseDetectingRefreshTokenAuthenticationProvider(AuthenticationProvider delegate,
                                                            RefreshTokenService refreshTokenService) {
        this.delegate = delegate;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public Authentication authenticate(@NonNull Authentication authentication) throws AuthenticationException {

        OAuth2RefreshTokenAuthenticationToken request =
                (OAuth2RefreshTokenAuthenticationToken) authentication;

        // 제시된 refresh 토큰 값 (= 우리 jti)
        String presentedValue = request.getRefreshToken();

        // ── BEFORE: 재사용 탐지 ────────────────────────────────
        // TODO: refreshTokenService 에 "재사용이면 family revoke + 예외" 하는 탐지 메서드를 하나 추가하고 호출.
        //   - 대장부에 presentedValue 가 CONSUMED 로 있으면 → revokeFamily + throw
        //       (SAS에 던질 땐 OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT) 로 감싸는 게 규격에 맞음)
        //   - ACTIVE 이거나 대장부에 없으면 → 그냥 통과 (아래 delegate 로)
        //   ※ 왜 BEFORE 인가: 재사용된 옛 토큰은 delegate 안에서 findByToken=null 로 즉시 invalid_grant 되어
        //      AFTER 까지 못 온다. 그래서 우리 대장부를 "먼저" 봐야 잡힌다.

        try{
            refreshTokenService.assertNotReused(presentedValue);
        }catch (RefreshTokenReuseException refreshTokenReuseException){
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }


        // ── DELEGATE: 진짜 SAS provider 가 회전 수행 ─────────────
        Authentication result = this.delegate.authenticate(authentication);

        // ── AFTER: 회전 성공 → 대장부 갱신 ──────────────────────
        // TODO: 결과에서 새 refresh 토큰 값/만료를 꺼내 rotate 호출.
        //   OAuth2AccessTokenAuthenticationToken accessTokenResult = (OAuth2AccessTokenAuthenticationToken) result;
        //   var newRt = accessTokenResult.getRefreshToken();      // OAuth2RefreshToken (없을 수도: reuseRefreshTokens=true면 회전 안 함 → null)
        //   if (newRt != null) {
        //       refreshTokenService.rotate(presentedValue, newRt.getTokenValue(), newRt.getExpiresAt());
        //   }
        //   → presented 는 CONSUMED, 새것은 ACTIVE 로 같은 family 아래 기록됨.

        OAuth2AccessTokenAuthenticationToken accessTokenResult = (OAuth2AccessTokenAuthenticationToken) result;

        OAuth2RefreshToken refreshTokenToken = accessTokenResult.getRefreshToken();

        if (refreshTokenToken != null) {
            refreshTokenService.rotate(presentedValue,
                    refreshTokenToken.getTokenValue(), refreshTokenToken.getExpiresAt());
        }

        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {

        return this.delegate.supports(authentication);
    }
}
