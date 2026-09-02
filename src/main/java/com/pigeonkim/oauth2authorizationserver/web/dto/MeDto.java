package com.pigeonkim.oauth2authorizationserver.web.dto;

/**
 * GET /me 응답. me.html 이 읽는다.
 *
 */
public record MeDto(String email, String displayName, String phoneNumber) {
}
