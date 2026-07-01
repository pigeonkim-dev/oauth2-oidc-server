# T5 — 토큰 클레임 커스터마이저 설계문서 (초안)

> 상태: **초안**(플랜 기반). 선행: T1~T4.

## 1. 목표
발급되는 액세스 토큰(JWT)에 우리 계정 정보를 클레임으로 실어 보냄. 클라이언트(더미 SPA)가 토큰만으로 "누구인지"를 알게.

## 2. 메커니즘 — `OAuth2TokenCustomizer`
SAS가 제공하는 확장점. JWT 인코딩 직전 클레임 추가:
```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(...) {
    return ctx -> {
        if (ctx.getTokenType() == OAuth2TokenType.ACCESS_TOKEN) {
            // ctx.getClaims().claim("name", ...), .claim("account_id", ...)
        }
    };
}
```

## 3. 클레임 (안 — 정제 시 확정)
| 클레임 | 값 | 비고 |
|---|---|---|
| sub | account id | 주체 = 사람(Account) |
| name | displayName | 가입 시 수집 |
| (옵션) providers | 연결된 credential 타입들 | EMAIL_PASSWORD/KAKAO… |
| email | 검증된 이메일 | email_verified=true 인 것만 |

- ⚠️ PII 최소화: 토큰에 꼭 필요한 것만. 민감정보 남발 금지.

## 4. N+1 방지 — `@EntityGraph`
클레임 만들 때 Account + 연결된 credentials를 **한 쿼리로** 로드:
```java
@EntityGraph(attributePaths = "credentials")
Optional<Account> findWithCredentialsById(Long id);
```
- ⚠️ 단, T1 Account는 현재 **단방향**(credentials 컬렉션 없음). T5 들어갈 때 Account에 `@OneToMany(mappedBy="account") List<Credential>` 추가 필요. (T1 설계문서의 "필요해지면 추가" 지점.)

## 5. ★ 테스트
- 액세스 토큰에 sub/name 클레임이 의도대로 박히는지 (통합/슬라이스)
- 연결 credential 로드가 단일 쿼리인지 (N+1 회귀 방지)

## 6. 정제 시 결정할 것
- 정확한 클레임 셋(특히 email/providers 노출 여부)
- Account에 credentials 컬렉션 추가(양방향) 시 equals/hashCode·순환 주의(T1 Lombok 함정 재확인)
