package com.pigeonkim.oauth2authorizationserver.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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
     * 평문(정규화 전) → 해당 필드의 blind index 문자열.
     *
     * @param field    무엇을 색인하는가. 정규화 규칙과 도메인 분리가 여기서 결정된다.
     * @param rawValue 사용자가 입력한 원문
     * @return blind index. rawValue 가 null/blank 면 null
     */
    public String hash(IndexedField field, String rawValue) {

        if (rawValue == null) {
            return null;
        }

        // TODO 1: 필드별 정규화로 교체
        //   기존: rawValue.replaceAll("[^0-9]", "")  ← 전화 전용이라 이메일/이름이 전부 빈 문자열이 됐다
        //   변경: field.normalize(rawValue)
        //   정규화 결과가 비었으면(blank) null 을 반환할지 결정할 것.
        //   ※ 빈 문자열을 그대로 해싱하면 "값 없는 것들"이 전부 같은 인덱스가 되어
        //     유니크 제약에 걸리거나 검색이 전부 매칭된다. 지금 이메일에서 터지던 게 정확히 이것.
        rawValue = field.normalize(rawValue);

        if (rawValue.isBlank()) {
            throw new IllegalArgumentException("잘못 된 입력 입니다.");
        }

        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "HmacSHA256");
        Mac mac = null;

        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        // TODO 2: 도메인 분리 — HMAC 입력에 필드 이름을 섞는다
        //   지금은 정규화값만 해싱하므로, 전화 "01012345678" 과
        //   이름 정규화 결과가 우연히 같으면 두 컬럼에서 같은 인덱스가 나온다.
        //   해결: field.name() 을 구분자와 함께 앞에 붙인다.  예) "EMAIL:alice@gmail.com"
        //   구분자로 ':' 이 안전한 이유 — enum 이름은 [A-Z_] 뿐이라 ':' 을 절대 포함하지 않는다.
        //   따라서 "field 부분"과 "값 부분"의 경계가 모호해질 수 없다.
        //
        // TODO 3: getBytes() 에 인코딩을 명시한다
        //   인자 없는 getBytes() 는 '플랫폼 기본 인코딩'을 쓴다.
        //   지금까지는 숫자만 남겼으니 안 터졌지만, 한글 이름이 들어오는 순간
        //   운영체제/로케일에 따라 같은 값이 다른 바이트가 되어 해시가 달라진다.
        //   StandardCharsets.UTF_8 을 넘길 것.

        rawValue = field.name() + ":" + rawValue;

        byte[] macResult = mac.doFinal(rawValue.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(macResult);
    }
}
