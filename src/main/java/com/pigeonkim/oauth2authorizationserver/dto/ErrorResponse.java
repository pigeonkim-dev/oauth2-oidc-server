package com.pigeonkim.oauth2authorizationserver.dto;

import java.util.Map;

public record ErrorResponse(
        int status,
        String message,
        Map<String, String> fieldErrors
) {
}
