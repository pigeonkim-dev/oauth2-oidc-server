package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;

public record VerificationIssuedEvent(
        Long challengeId, VerificationChannel channel,
        String destination, String rawCode) {
}

