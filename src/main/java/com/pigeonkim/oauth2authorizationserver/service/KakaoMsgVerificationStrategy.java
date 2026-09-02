package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KakaoMsgVerificationStrategy implements VerificationStrategy {
    @Override public VerificationChannel channel() {
        return VerificationChannel.KAKAO_MSG;
    }

    @Override public void send(String destination, String rawCode) {
        throw new UnsupportedOperationException("KAKAO_MSG not implemented in v1");
    }
}
