package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailVerificationStrategy implements VerificationStrategy {

    @Override
    public VerificationChannel channel() {
        return VerificationChannel.EMAIL;
    }

    @Override
    public void send(String destination, String rawCode) {
        // v1 dev 모드: SMTP 대신 로그 (운영 전 이 줄 제거 — TODO)
        log.info("[DEV] verification code to {} = {}", destination, rawCode);
    }
}
