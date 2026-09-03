package com.pigeonkim.oauth2authorizationserver.service;

import java.io.Serializable;

public record PendingKakaoCredential(String provider, String providerUid, String email) implements Serializable {
}