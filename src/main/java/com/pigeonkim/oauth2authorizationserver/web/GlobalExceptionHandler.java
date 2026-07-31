package com.pigeonkim.oauth2authorizationserver.web;

import com.pigeonkim.oauth2authorizationserver.dto.ErrorResponse;
import com.pigeonkim.oauth2authorizationserver.exception.DuplicateCredentialException;
import com.pigeonkim.oauth2authorizationserver.exception.LinkRefusedException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리. 컨트롤러에서 튀어나온 예외를 여기서 가로채
 * 통일된 ErrorResponse(JSON) + 적절한 HTTP 상태코드로 변환한다.
 * (ASP.NET 의 ExceptionFilter / UseExceptionHandler 미들웨어에 해당)
 *
 * 규칙: @ExceptionHandler(예외.class) 메서드 하나 = 예외 한 종류의 응답 매핑.
 * 여러 개가 매칭되면 "가장 구체적인 타입"이 이긴다 (Exception.class 는 최후의 그물).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1) 중복 가입 → 409 Conflict   ※ 이 메서드는 "정답 예시"로 채워 둠. 나머지는 이걸 참고해 직접.
    @ExceptionHandler(DuplicateCredentialException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateCredentialException ex) {
        ErrorResponse body = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // 2) @Valid 검증 실패 → 400 Bad Request + 필드별 메시지
    //    (email 형식 틀림 / password 8자 미만 같은 걸 필드별로 돌려준다)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // TODO:
        //  a) Map<String,String> fieldErrors = new HashMap<>();
        //  b) ex.getBindingResult().getFieldErrors() 를 for 로 순회하며
        //       fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        //     ( fe.getDefaultMessage() = @Size(message="비밀번호는 8자 이상") 에 쓴 그 메시지 )
        //  c) new ErrorResponse(400, "검증 실패", fieldErrors) 를 400 으로 반환

        Map<String, String> errors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse body = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "검증실패", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    // 3) 링킹 거부 → 409 Conflict (또는 422 Unprocessable — 규칙 정하기 나름)
    @ExceptionHandler(LinkRefusedException.class)
    public ResponseEntity<ErrorResponse> handleLinkRefused(LinkRefusedException ex) {
        // TODO: 1) 을 참고해서 CONFLICT(또는 원하는 상태)로 반환
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_CONTENT.value(),  ex.getMessage(), null);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(body);
    }

    // 4) 그 외 예측 못한 예외 → 500 (최후의 그물)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // TODO:
        //  - log.error("unhandled exception", ex);   ← 원인/스택은 '로그에만' 남긴다
        //  - 응답 바디엔 상세/스택 절대 노출 금지: new ErrorResponse(500, "서버 오류가 발생했습니다", null)
        //  - INTERNAL_SERVER_ERROR 로 반환

        log.error(ex.getMessage(), ex);

        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),  "Server Error", null);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
}
