package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.VerificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailVerificationStrategy implements VerificationStrategy {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailVerificationStrategy(JavaMailSender mailSender,
                                     @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public VerificationChannel channel() {

        return VerificationChannel.EMAIL;
    }

    @Override
    public void send(String destination, String rawCode) {
        log.info("[DEV] verification code to {} = {}", destination, rawCode);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destination);
        message.setSubject("[인증코드] 인증코드를 입력 하고 로그인 하세요.");
        message.setText("인증코드 : "+ rawCode);

        mailSender.send(message);
    }
}
