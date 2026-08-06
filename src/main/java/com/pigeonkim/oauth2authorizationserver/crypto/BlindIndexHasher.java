package com.pigeonkim.oauth2authorizationserver.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * blind index 생성기 — PII(전화)의 '검색용' 결정적 해시.
 * <p>
 * 암호문(StringEncryptConverter)은 랜덤이라 그 컬럼으론 검색이 안 된다. 그래서 검색이 필요한 전화는
 * 평문을 정규화한 뒤 HMAC-SHA256 해서 별도 컬럼(phone_idx)에 같이 저장한다.
 * 같은 전화 → 같은 인덱스 → 관리자 동등검색 가능. HMAC 이라 원문 복원은 불가.
 * <p>
 * ★ 정규화(중요): 저장할 때와 검색할 때 '같은 규칙'으로 정규화해야 인덱스가 일치한다.
 * 전화 예: 하이픈/공백 제거하고 숫자만 → "010-1234-5678" → "01012345678".
 * (국가코드 +82 처리 규칙은 직접 정한다.)
 * <p>
 * 키: HMAC 키는 암호화 키와 '다른' 값을 쓴다(app.crypto.index-key, base64). 배포 시 env 로 덮어씀.
 */
@Component
public class BlindIndexHasher {

    private final String base64Key;

    public BlindIndexHasher(@Value("${app.crypto.index-key}") String base64Key) {
        this.base64Key = base64Key;
    }

    /**
     * 평문(정규화 전) → blind index 문자열.
     */
    public String hash(String rawValue) {
        // TODO:
        //   1) 정규화(전화: 숫자만 남김) — null/blank 는 null 반환
        //   2) base64Key 디코딩 → SecretKeySpec(..., "HmacSHA256"), Mac.getInstance("HmacSHA256").init(key)
        //   3) 정규화값을 HMAC → 결과 바이트를 hex(또는 base64)로 인코딩해 반환

        if (rawValue == null) {
            return null;
        }

        rawValue = rawValue.replaceAll("[^0-9]", "");

        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "HmacSHA256");
        Mac mac = null;

        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }


        byte[] macResult = mac.doFinal(rawValue.getBytes());

        return Base64.getEncoder().encodeToString(macResult);
    }
}
