package com.pigeonkim.oauth2authorizationserver.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 로그인 "화면" 컨트롤러.
 *
 * 로그인 인증 처리(POST /login)는 우리가 짜지 않는다 — Spring Security 의 formLogin 필터가
 * username/password 를 받아 CredentialUserDetailsService 로 인증한다.
 * 여기선 커스텀 로그인 화면(GET /login)을 렌더링하는 것만 담당한다.
 * (SecurityConfig 의 formLogin().loginPage("/login") 이 이 경로를 로그인 페이지로 지정한다.)
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login"; // templates/login.html
    }
}
