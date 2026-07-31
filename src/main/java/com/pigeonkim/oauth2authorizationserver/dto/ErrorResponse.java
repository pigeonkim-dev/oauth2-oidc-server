package com.pigeonkim.oauth2authorizationserver.dto;

import java.util.Map;

/**
 * 통일된 에러 응답 바디. 모든 실패 응답은 이 모양의 JSON 으로 나간다.
 *
 *  {
 *    "status": 409,
 *    "message": "already registered",
 *    "fieldErrors": null            // @Valid 실패일 때만 { "email": "...", "password": "..." }
 *  }
 *
 * - status      : HTTP 상태코드(숫자). 바디에도 넣어두면 클라이언트가 파싱하기 편함.
 * - message     : 사람이 읽을 요약. (단, 서버 내부 상세/스택은 절대 넣지 말 것)
 * - fieldErrors : @Valid 검증 실패 시 필드명→메시지. 그 외 에러는 null.
 */
public record ErrorResponse(
        int status,
        String message,
        Map<String, String> fieldErrors
) {
    // TODO(선택 연습): fieldErrors 가 없는 흔한 경우를 위해
    //   정적 팩토리 of(int status, String message) 를 만들어
    //   내부에서 new ErrorResponse(status, message, null) 을 반환하게 하면
    //   호출부에서 매번 null 을 넘기지 않아도 된다. (Credential.basic() 같은 팩토리 패턴 복습)
}
