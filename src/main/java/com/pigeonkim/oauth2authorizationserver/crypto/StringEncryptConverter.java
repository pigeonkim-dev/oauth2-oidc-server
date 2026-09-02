package com.pigeonkim.oauth2authorizationserver.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA AttributeConverter — 엔티티의 String 필드를 DB 저장 시 암호화, 읽을 때 복호화.
 * 대상: Account.phoneNumber (PII). 필드에 @Convert(converter = StringEncryptConverter.class) 로 건다.
 * <p>
 * 알고리즘(권장): AES-256-GCM.
 * - 저장마다 랜덤 IV(12바이트) → 같은 평문도 매번 다른 암호문(= 랜덤 암호화). 그래서 이 컬럼으론 검색 불가.
 * - 저장 형식: base64( IV(12) || ciphertext || GCM tag ). 복호화 때 앞 12바이트를 IV로 분리.
 * <p>
 * ★ 키 주입 seam (이번 핵심): AttributeConverter 는 보통 Hibernate 가 직접 생성해서 Spring 주입이 안 된다.
 * 그런데 Spring Boot 는 @Component 로 등록된 컨버터를 Hibernate 에 건네준다 → 그래서 @Value 주입이 먹는다.
 * 키는 하드코딩 금지. gitignore 된 application-local.yml 의 app.crypto.data-key(base64 32바이트)에서 주입,
 * 배포 시엔 env(APP_CRYPTO_DATA_KEY)로 덮어쓴다. (H0 secret 외부화와 직결)
 */
@Component
@Converter
public class StringEncryptConverter implements AttributeConverter<String, String> {

    private final String base64Key;

    public StringEncryptConverter(@Value("${app.crypto.data-key}") String base64Key) {
        this.base64Key = base64Key;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {

        if (plaintext == null) {
            return null;
        }

        try {

            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            byte[] iv = new byte[12];

            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();

            return Base64.getEncoder().encodeToString(combined);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException |
                 InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbValue) {

        if (dbValue == null) {
            return null;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(dbValue);
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);

            byte[] keyBytes = Base64.getDecoder().decode(base64Key);

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec);

            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException |
                 InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

    }
}
