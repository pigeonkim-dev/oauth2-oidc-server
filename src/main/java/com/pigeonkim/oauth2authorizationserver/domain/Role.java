package com.pigeonkim.oauth2authorizationserver.domain;

/**
 * 계정 권한. Spring Security 는 "ROLE_" 접두 authority 로 hasRole("ADMIN") 을 판정한다.
 * enum 이름을 authority 문자열과 똑같이 두면(name() == "ROLE_ADMIN") 변환이 단순해진다.
 * 지금은 단일 컬럼(Account.role). 권한이 많아지면 그때 authorities 테이블로 분리(플랜).
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
