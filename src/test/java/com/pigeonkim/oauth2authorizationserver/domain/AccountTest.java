package com.pigeonkim.oauth2authorizationserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Account 도메인 단위 테스트 (Spring 없이 plain JUnit — 엔티티는 순수 도메인).
 * 검증: 팩토리 기본값(ROLE_USER / PENDING) + changePhone 이 두 필드를 저장하는가.
 */
class AccountTest {

    @Test
    void 팩토리는_신규가입_기본값을_세팅한다() {


        Account account = Account.of("홍길동");

        assertEquals(Role.ROLE_USER, account.getRole());
        assertEquals(AccountStatus.PENDING, account.getStatus());
        assertEquals("홍길동", account.getDisplayName());

    }

    @Test
    void changePhone_은_전화번호와_blind_index를_저장한다() {

        Account account = Account.of("홍길동");

        account.changePhone("01012345678", "someIdxHash");

        assertEquals("01012345678", account.getPhoneNumber());
        assertEquals("someIdxHash", account.getPhoneIdx());
    }
}
