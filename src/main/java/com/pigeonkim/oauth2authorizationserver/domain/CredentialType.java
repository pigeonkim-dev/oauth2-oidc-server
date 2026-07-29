package com.pigeonkim.oauth2authorizationserver.domain;

public enum CredentialType {
    EMAIL_PASSWORD,
    OAUTH        // 소셜 로그인 일반(kakao/google/naver…) — 구체 provider 는 Credential.provider 컬럼이 구분
}
