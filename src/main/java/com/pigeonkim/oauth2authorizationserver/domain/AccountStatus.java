package com.pigeonkim.oauth2authorizationserver.domain;

/**
 * 계정 상태.
 * PENDING   : 가입했으나 이메일 검증 전(반쪽 계정). 로그인 제한.
 * ACTIVE    : 정상.
 * BLOCKED   : 관리자가 정지시킴. 로그인 차단.
 * WITHDRAWN : 탈퇴(soft delete tombstone). 로그인 차단 + 개인정보 마스킹.
 */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    BLOCKED,
    WITHDRAWN
}
