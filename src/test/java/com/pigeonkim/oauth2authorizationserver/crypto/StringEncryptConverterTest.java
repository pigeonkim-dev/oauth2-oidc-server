package com.pigeonkim.oauth2authorizationserver.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * StringEncryptConverter 단위 테스트 (Spring 없이 plain JUnit).
 * 컴파일이 아니라 '실제로 맞게 도는지'를 증명한다: 왕복 + 랜덤 IV + null 처리.
 */
class StringEncryptConverterTest {

    private StringEncryptConverter converter;

    /** AES-256 이라 디코딩 시 32바이트가 되도록 랜덤 키 생성(테스트 전용). */
    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @BeforeEach
    void setUp() {
        converter = new StringEncryptConverter(randomBase64Key());
    }

    @Test
    void 암호화_후_복호화하면_원본이_나온다() {

        String rawValue ="010-1234-5678";

      String ecryptedString =  converter.convertToDatabaseColumn(rawValue);
      String decryptedString = converter.convertToEntityAttribute(ecryptedString);

      assertEquals(rawValue, decryptedString);

    }

    @Test
    void 같은_값도_매번_다른_암호문이_된다_랜덤IV() {

        String rawValue ="010-1234-5678";

        String result1 = converter.convertToDatabaseColumn(rawValue);
        String result2 = converter.convertToDatabaseColumn(rawValue);

        assertNotEquals(result1, result2);
    }

    @Test
    void null_은_null_로_처리된다() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
