# T1 — Account / Credential 도메인 설계문서

> 상태: 구현 완료(단위 테스트 통과). 첫 커밋 7f1b223.
> 패키지: `com.pigeonkim.oauth2authorizationserver.domain`

## 1. 핵심 결정 — Account(사람) ↔ Credential(증명수단) 분리

정체성 중심 모델. **사람 1명(Account)** 이 **여러 증명수단(Credential)** 을 가짐 (1:N).
```
account(1) ───< (N)credential
```
- 왜: 자격증명 중심으로 짜면 나중에 "한 사람의 여러 로그인 수단 연결(linking)"을 retrofit할 때 마이그레이션 지옥. 처음부터 사람↔증명 분리.

## 2. Account

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | IDENTITY |
| displayName | String(TEXT) | 가입 시 수집 |
| createdAt | LocalDateTime | @CreationTimestamp |

- `@NoArgsConstructor(PROTECTED)`, equals/hashCode = id 기반(instanceof, 상수 hashCode).
- **단방향**: credentials 컬렉션 없음(Credential의 @ManyToOne만). 필요해지면 추가.
- ⚠️ createdAt이 `LocalDateTime`인데 T2는 `Instant`(UTC). 통일 권장(미적용, 차단 아님).

## 3. Credential — 단일테이블 STI

**single class + `type` enum 판별** (JPA `@Inheritance` 안 씀). `CredentialType = { EMAIL_PASSWORD, KAKAO }`.

| 필드 | 타입 | null | 비고 |
|---|---|---|---|
| id | Long | PK | IDENTITY |
| account | @ManyToOne(LAZY) | NOT NULL(FK) | account_id |
| type | CredentialType(STRING) | | 판별자 |
| email | varchar(255) | **nullable** | 카카오 폰가입 시 없음 |
| passwordHash | String | nullable | EMAIL_PASSWORD만 |
| provider | String | nullable | KAKAO만 |
| providerUid | String | nullable | KAKAO만 |
| emailVerified | boolean | NOT NULL | 검증 성공 시 true |
| createdAt | LocalDateTime | | @CreationTimestamp |

### 제약
- 복합 unique `(type, email)` — EMAIL_PASSWORD 유일성 (NULL은 Postgres가 서로 다르게 취급 → 카카오 null email 다중 허용)
- 복합 unique `(provider, provider_uid)` — 카카오 식별
- **단일 컬럼 unique 금지** (같은 이메일이 EMAIL_PASSWORD+KAKAO로 공존 = 정상 링킹)

### nullable 갭 무결성 — 이중 강제 (STI의 대가)
단일테이블이라 DB가 "EMAIL_PASSWORD면 passwordHash 필수" 같은 조건부 NOT NULL을 컬럼으로 못 검. 그래서 앱이 책임:
1. **정적 팩토리** (`Credential.basic()` / `Credential.kakao()`) — private 생성자, 잘못된 조합을 *만들 수 없게*. `@Builder` 금지(우회로 차단).
   - `basic(account, email, passwordHash)` — email+passwordHash 필수
   - `kakao(account, email, provider, providerUid)` — provider+providerUid 필수, email 선택
2. **`@AssertTrue isFieldCombinationValid()`** — flush 시점에 Hibernate Validator가 모순 거부.
   - EMAIL_PASSWORD → email+passwordHash 있고 provider/uid 없음
   - KAKAO → provider+uid 있고 passwordHash 없음 (email 선택)
3. (선택, 미적용) DB `@Check` 제약 — 가장 강한 3겹째. 운영 강화 시 고려.

### 기타
- `markEmailVerified()` — emailVerified=true (T2 검증 성공 시 호출)
- equals/hashCode = id 기반(T2 엔티티와 동일 관용구)
- `@NoArgsConstructor(PROTECTED)` — JPA 전용, 외부는 팩토리만

## 4. Repository

```java
// CredentialRepository
Optional<Credential> findByEmailAndType(String email, CredentialType type);          // 로그인/조회
Optional<Credential> findByProviderAndProviderUid(String provider, String providerUid); // 카카오
boolean existsByEmailAndType(String email, CredentialType type);                       // 가입 중복(타입 스코프)
Optional<Credential> findByAccountAndType(Account account, CredentialType type);       // T2 emailVerified 매핑
// AccountRepository = 최소 JpaRepository
```

## 5. 다음으로 이어지는 것
- email 단독 unique를 일부러 뺀 것 → **T3 링킹**(같은 이메일 다중 credential)의 토대
- 검증 안 된 email 일치로 자동연결 **금지**(pre-hijacking) → T3에서 강제
- emailVerified → T2 검증이 채움(완료)
