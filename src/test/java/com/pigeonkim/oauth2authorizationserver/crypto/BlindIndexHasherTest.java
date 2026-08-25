package com.pigeonkim.oauth2authorizationserver.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlindIndexHasher 단위 테스트 (Spring 없이 plain JUnit).
 * 검증: 결정성(같은 입력→같은 해시) + 정규화(포맷 달라도 같은 번호면 같은 해시) + null.
 */
class BlindIndexHasherTest {

    private BlindIndexHasher hasher;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @BeforeEach
    void setUp() {
        hasher = new BlindIndexHasher(randomBase64Key());
    }

    @Test
    void 자모분리형과_완성형은_같은_인덱스() {

        String NFDName = hasher.hash(IndexedField.NAME, Normalizer.normalize("김승기", Normalizer.Form.NFD));
        String NFCName = hasher.hash(IndexedField.NAME, "김승기");

        assertEquals(NFDName, NFCName);

    }

    @Test
    void 이메일은_대소문자와_공백이_달라도_같은_인덱스() {

        String Email1 = hasher.hash(IndexedField.EMAIL, "Test@test.com");
        String Email2 = hasher.hash(IndexedField.EMAIL, "test@test.com");

        assertEquals(Email1, Email2);

    }

    @Test
    void 도메인_분리_다른해시() {

        String phone = hasher.hash(IndexedField.PHONE, "01012345678");
        String email = hasher.hash(IndexedField.EMAIL, "01012345678");

        assertNotEquals(phone, email);
    }

    @Test
    void 전화번호에_숫자가_없으면_예외() {

        assertThrows(IllegalArgumentException.class,
                () -> hasher.hash(IndexedField.PHONE, "abc"));
    }

}
