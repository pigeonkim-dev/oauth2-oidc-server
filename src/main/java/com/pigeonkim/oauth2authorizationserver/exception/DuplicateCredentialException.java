package com.pigeonkim.oauth2authorizationserver.exception;

public class DuplicateCredentialException extends RuntimeException {
    public DuplicateCredentialException(String message) {
        super(message);
    }
}