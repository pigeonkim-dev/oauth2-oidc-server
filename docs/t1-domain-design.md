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
- **양방향**(T5에서 추가): `@OneToMany(mappedBy="account") List<Credential> credentials`. 클레임 로드용 `@EntityGraph findWithCredentialsById`. (⚠️ Lombok @ToString/@EqualsAndHashCode에 credentials 포함 금지 — 순환/N+1)
- ⚠️ createdAt이 `LocalDateTime`인데 T2는 `Instant`(UTC). 통일 권장(미적용, 차단 아님).

## 3. Credential — 단일테이블 STI

**single class + `type` enum 판별** (JPA `@Inheritance` 안 씀). `CredentialType = { EMAIL_PASSWORD, OAUTH }`. (소셜 일반화 — §6 결정 로그)

| 필드 | 타입 | null | 비고 |
|---|---|---|---|
| id | Long | PK | IDENTITY |
| account | @ManyToOne(LAZY) | NOT NULL(FK) | account_id |
| type | CredentialType(STRING) | | 판별자 |
| email | varchar(255) | **nullable** | 카카오 폰가입 시 없음 |
| passwordHash | String | nullable | EMAIL_PASSWORD만 |
| provider | String | nullable | OAUTH(소셜)만 — "kakao"/"google"… |
| providerUid | String | nullable | OAUTH(소셜)만 |
| emailVerified | boolean | NOT NULL | 검증 성공 시 true |
| createdAt | LocalDateTime | | @CreationTimestamp |

### 제약
- 복합 unique `(type, email)` — EMAIL_PASSWORD 유일성 (NULL은 Postgres가 서로 다르게 취급 → 카카오 null email 다중 허용)
- 복합 unique `(provider, provider_uid)` — 카카오 식별
- **단일 컬럼 unique 금지** (같은 이메일이 EMAIL_PASSWORD+OAUTH로 공존 = 정상 링킹)

### nullable 갭 무결성 — 이중 강제 (STI의 대가)
단일테이블이라 DB가 "EMAIL_PASSWORD면 passwordHash 필수" 같은 조건부 NOT NULL을 컬럼으로 못 검. 그래서 앱이 책임:
1. **정적 팩토리** (`Credential.basic()` / `Credential.oauth()`) — private 생성자, 잘못된 조합을 *만들 수 없게*. `@Builder` 금지(우회로 차단).
   - `basic(account, email, passwordHash)` — email+passwordHash 필수
   - `oauth(account, email, provider, providerUid)` — provider+providerUid 필수, email 선택 (소셜 공통)
2. **`@AssertTrue isFieldCombinationValid()`** — flush 시점에 Hibernate Validator가 모순 거부.
   - EMAIL_PASSWORD → email+passwordHash 있고 provider/uid 없음
   - OAUTH → provider+uid 있고 passwordHash 없음 (email 선택)
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

## 6. 결정 로그 — 소셜 크레덴셜 일반화 & 인증 두 축 (2026-07-29 확정)

> 기획 반영용 요약. 리팩터 완료(CredentialType / Credential / LinkingService 3파일).

- **`CredentialType.KAKAO` → `OAUTH` 로 일반화.** 소셜 로그인(kakao/google/naver/apple…)은 전부 `provider + providerUid + (선택)email` 구조가 동일 → provider별 팩토리(`kakao()`)를 두지 않고 **`Credential.oauth(account, email, provider, providerUid)` 하나**로 통일. 구체 provider 는 `provider` 컬럼("kakao"/"google")이 구분. **새 소셜 추가 = 호출 인자만, 코드 변경 0.**
- **인증의 두 축을 분리(혼동 주의):**
  - ① **로그인 크레덴셜**(`Credential.type`): *어떻게 로그인하나* — `EMAIL_PASSWORD`, `OAUTH`.
  - ② **검증 채널**(`VerificationChannel`): *"채널 통제"를 어디로 증명하나* — `EMAIL`, `SMS`, `KAKAO_MSG`.
- **전화/SMS = MFA(2차 요소) 방향.** 전화 인증은 새 로그인 타입이 아니라 **검증 채널(SMS)을 2차 요소로** 쓰는 MFA로 본다. v1 미구현(`SmsVerificationStrategy` 스텁 유지), 채널 추상화가 향후 MFA 토대. **지금 모델 변경 없음.**
- **v1 범위:** 로그인 = `EMAIL_PASSWORD` + `OAUTH`. 검증 채널 = `EMAIL`(실구현) + `SMS`(스텁). 전화 로그인 / 실 SMS 게이트웨이는 미리 준비 안 함.
