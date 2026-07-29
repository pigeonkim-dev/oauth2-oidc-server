package com.pigeonkim.oauth2authorizationserver.web;

import com.pigeonkim.oauth2authorizationserver.dto.SignupRequest;
import com.pigeonkim.oauth2authorizationserver.dto.VerifyEmailRequest;
import com.pigeonkim.oauth2authorizationserver.service.SignupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    // POST /api/signup
    @PostMapping
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        signupService.signup(request.email(), request.password(), request.displayName());
        return ResponseEntity.accepted().build();          // 202: 접수됨(이메일 검증 대기)
    }

    // POST /api/signup/verify-email
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        boolean ok = signupService.verifyEmail(request.email(), request.code());
        return ok ? ResponseEntity.ok().build()            // 200: 검증 성공
                : ResponseEntity.badRequest().build();   // 400: 실패
    }
}