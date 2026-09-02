package com.pigeonkim.oauth2authorizationserver.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LinkInitiateRequest(
        @NotBlank @Email String existingEmail) {
}
