package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;
import org.springframework.stereotype.Component;

@Component
public class SmsVerificationStrategy implements VerificationStrategy {
    @Override
    public VerificationChannel channel() {
        return VerificationChannel.SMS;
    }

    @Override
    public void send(String destination, String rawCode) {
        throw new UnsupportedOperationException("KAKAO_MSG not implemented in v1");
    }
}
