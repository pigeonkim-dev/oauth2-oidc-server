package com.pigeonkim.oauth2authorizationserver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank String code
) {}