package com.pigeonkim.oauth2authorizationserver.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상") String password,
        @NotBlank @Size(max = 50) String displayName
) {}