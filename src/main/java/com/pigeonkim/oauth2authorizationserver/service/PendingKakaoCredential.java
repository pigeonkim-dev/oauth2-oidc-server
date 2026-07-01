package com.pigeonkim.oauth2authorizationserver.service;

public record PendingKakaoCredential(String provider, String providerUid, String email) {
}