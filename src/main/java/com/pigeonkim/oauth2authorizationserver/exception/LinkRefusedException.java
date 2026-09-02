package com.pigeonkim.oauth2authorizationserver.exception;

public class LinkRefusedException extends RuntimeException {
    public LinkRefusedException(String message) {
        super(message);
    }
}