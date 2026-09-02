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
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

import java.util.Objects;


public class ReuseDetectingRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final RefreshTokenService refreshTokenService;

    public ReuseDetectingRefreshTokenAuthenticationProvider(AuthenticationProvider delegate,
                                                            RefreshTokenService refreshTokenService) {
        this.delegate = delegate;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public Authentication authenticate(@NonNull Authentication authentication) throws AuthenticationException {

        OAuth2RefreshTokenAuthenticationToken request =
                (OAuth2RefreshTokenAuthenticationToken) authentication;

        String presentedValue = request.getRefreshToken();

        try{
            refreshTokenService.assertNotReused(presentedValue);
        }catch (RefreshTokenReuseException refreshTokenReuseException){
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        Authentication result = this.delegate.authenticate(authentication);

        OAuth2AccessTokenAuthenticationToken accessTokenResult = (OAuth2AccessTokenAuthenticationToken) result;

        OAuth2RefreshToken refreshTokenToken = Objects.requireNonNull(accessTokenResult).getRefreshToken();

        if (refreshTokenToken != null) {
            refreshTokenService.rotate(presentedValue,
                    refreshTokenToken.getTokenValue(), refreshTokenToken.getExpiresAt());
        }

        return result;
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication) {

        return this.delegate.supports(authentication);
    }
}
