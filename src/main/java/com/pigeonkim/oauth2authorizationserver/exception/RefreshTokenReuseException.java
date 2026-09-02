package com.pigeonkim.oauth2authorizationserver.exception;

public class RefreshTokenReuseException extends RuntimeException {
    public RefreshTokenReuseException(String jti) {
        super("refresh token reuse detected: " + jti);
    }
}