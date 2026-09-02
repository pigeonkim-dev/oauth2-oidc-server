package com.pigeonkim.oauth2authorizationserver.web.handler;

import com.pigeonkim.oauth2authorizationserver.web.dto.ErrorResponse;
import com.pigeonkim.oauth2authorizationserver.exception.DuplicateCredentialException;
import com.pigeonkim.oauth2authorizationserver.exception.LinkRefusedException;

import com.pigeonkim.oauth2authorizationserver.exception.VerificationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DuplicateCredentialException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateCredentialException ex) {
        ErrorResponse body = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse body = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "검증실패", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    @ExceptionHandler(LinkRefusedException.class)
    public ResponseEntity<ErrorResponse> handleLinkRefused(LinkRefusedException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_CONTENT.value(),  ex.getMessage(), null);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {

        log.error(ex.getMessage(), ex);

        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),  "Server Error", null);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
    @ExceptionHandler(VerificationFailedException.class)
    public ResponseEntity<ErrorResponse> handleVerificationFailed(VerificationFailedException ex) {

        ErrorResponse body = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
