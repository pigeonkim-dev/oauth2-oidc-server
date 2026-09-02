package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;

public interface VerificationStrategy {
    VerificationChannel channel();

    void send(String destination, String rawCode);
}
