package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class VerificationSendListener {

    private final Map<VerificationChannel, VerificationStrategy> strategies;

    public VerificationSendListener(List<VerificationStrategy> list) {
        // 스프링이 전략 3개(EMAIL/KAKAO_MSG/SMS)를 List로 주입 → channel() 키로 Map 구성
        this.strategies = list.stream()
                .collect(Collectors.toMap(VerificationStrategy::channel, Function.identity()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssued(VerificationIssuedEvent e) {
        strategies.get(e.channel()).send(e.destination(), e.rawCode());
    }
}