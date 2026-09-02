package com.pigeonkim.oauth2authorizationserver.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 링킹 확인 요청 — 기존 계정에 온 LINK 코드를 입력해 '기존 계정 소유'를 증명.
 * ⚠️ '연결할 카카오 수단'은 여기에 없다. 클라이언트가 못 믿을 값이라
 *    서버 세션(카카오 OAuth로 증명됨)에서 꺼낸다. body 엔 기존계정 식별자 + 코드만.
 */
public record LinkConfirmRequest(
        @NotBlank @Email String existingEmail,
        @NotBlank String linkCode
) {}
