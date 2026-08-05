package com.pigeonkim.oauth2authorizationserver.web.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MeController {

    // GET /api/me — 현재 로그인한 사용자
    @GetMapping("/api/me")
    public Map<String, String> me(Authentication authentication) {
        return Map.of("email", authentication.getName());
    }
}
