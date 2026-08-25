package com.pigeonkim.oauth2authorizationserver.crypto;

import java.text.Normalizer;
import java.util.Locale;

/**
 * blind index 를 만들 대상 필드. 필드마다 '정규화 규칙'이 다르다.
 * <p>
 * ★ 이 enum 이 존재하는 이유 두 가지
 * <p>
 * 1) <b>정규화 규칙을 이름 옆에 붙여두기 위해.</b>
 * 정규화는 저장할 때와 조회할 때가 <b>완전히 같아야</b> 한다. 다르면 아무 오류 없이
 * "계정을 찾을 수 없습니다"만 뜬다. 규칙이 여기저기 흩어지면 반드시 어긋난다.
 * 새 필드를 추가하면 컴파일러가 normalize() 구현을 강제한다 — 빠뜨릴 수 없다.
 * <p>
 * 2) <b>도메인 분리(domain separation).</b>
 * 키가 하나뿐인데 전화와 이름을 같은 방식으로 해싱하면, 정규화 결과가 우연히 같은 값이
 * 서로 다른 컬럼에서 같은 인덱스를 갖는다. 그러면 "이 사람 전화번호와 저 사람 이름이
 * 같은 값"이라는 사실이 새어나간다. 필드 이름을 HMAC 입력에 섞어 그 상관관계를 끊는다.
 */
public enum IndexedField {

    /**
     * 이메일 — Contact(EMAIL) 의 value_idx.
     * 유일성의 진실원이 이 인덱스이므로 정규화가 어긋나면 같은 사람이 계정을 두 개 만든다.
     */
    EMAIL {
        @Override
        public String normalize(String raw) {

            if (raw == null) {
                return null;
            }

            raw = raw.trim();
            raw = raw.replaceAll("\\s+", "");
            raw = raw.toLowerCase(Locale.ROOT);

            return raw;
        }
    },

    /**
     * 전화번호 — Contact(PHONE) 의 value_idx.
     * 여기만 기존 동작(숫자만 남기기)과 같다.
     */
    PHONE {
        @Override
        public String normalize(String raw) {

            if (raw == null) {
                return null;
            }

            raw = raw.trim();
            raw = raw.replaceAll("\\s+", "");
            raw = raw.replaceAll("[^0-9]", "");

            return raw;
        }
    },

    /**
     * 실명 — Account.real_name_idx.
     * 사람이 입력하는 값이라 공백과 유니코드 표현이 제각각이다.
     */
    NAME {
        @Override
        public String normalize(String raw) {

            if (raw == null) {
                return null;
            }

            raw = raw.trim();
            raw = raw.replaceAll("\\s+", "");
            raw = Normalizer.normalize(raw, Normalizer.Form.NFC);

            return raw;
        }
    };

    /**
     * 원문을 이 필드의 규칙대로 정규화한다.
     * null/blank 판단은 호출자(BlindIndexHasher)가 먼저 하므로 여기서는 신경 쓰지 않아도 된다.
     */
    public abstract String normalize(String raw);
}
