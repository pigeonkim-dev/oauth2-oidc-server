package com.pigeonkim.oauth2authorizationserver.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void 같은_번호는_항상_같은_인덱스() {
        String result1 = hasher.hash("010-1234-5678");
        String result2 = hasher.hash("010-1234-5678");

        assertEquals(result1, result2);
    }

    @Test
    void 포맷이_달라도_같은_번호면_같은_인덱스_정규화() {

        String result1 = hasher.hash("010-1234-5678");
        String result2 = hasher.hash("01012345678");

        assertEquals(result1, result2);
    }

    @Test
    void 다른_번호는_다른_인덱스() {
        String result1 = hasher.hash("010-1234-5678");
        String result2 = hasher.hash("010-1234-5679");

        assertNotEquals(result1, result2);
    }

    @Test
    void null_은_null() {
        assertNull(hasher.hash(null));
    }
}
