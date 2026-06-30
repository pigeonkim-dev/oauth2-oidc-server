# T2 — Verification Subsystem 설계문서

> 상태: 잠김(plan-eng-review 통과, 2026-06-29). 구현 대상.
> 빌드 위치: `develop` 브랜치.
> 선행: T1 도메인(Account/Credential STI) 완료.

## 1. 목표

"이 **사람(Account)** 이 이 **채널(EMAIL/카카오/SMS)** 을 실제로 통제한다"를 증명한다.
1회분의 증명 시도를 `VerificationChallenge` 한 행으로 기록하고, 안전하게
(해시 저장 / 만료 / 일회용 / 무차별 대입 방어) 발급·검증한다.

핵심 설계 포인트:
- **채널 추상화** = `VerificationStrategy` (EMAIL 실구현, 카카오/SMS 스텁).
- **챌린지 수명주기** = 발급 → 검증 → 소비/무효화를 엔티티가 직접 강제.

## 2. 구성요소

```
domain/
  VerificationChannel      enum  EMAIL | KAKAO_MSG | SMS
  VerificationPurpose      enum  SIGNUP_VERIFY | LINK
  VerificationStatus       enum  PENDING | CONSUMED | SUPERSEDED
  VerificationChallenge    @Entity  (수명주기 불변식 보유)
repository/
  VerificationChallengeRepository
  CredentialRepository      ← findByAccountAndType 추가
service/
  VerificationStrategy      interface  (channel() + send())
  EmailVerificationStrategy implements  (실구현, v1은 로그 dev 모드)
  KakaoMsgVerificationStrategy / SmsVerificationStrategy  스텁(UnsupportedOperationException)
  VerificationService       글루 (issue / verify), Clock 주입
  VerificationIssuedEvent   ApplicationEvent (rawCode 메모리 전달)
  (AFTER_COMMIT 리스너)      이벤트 받아 strategy.send 호출
config/
  Clock 빈
```

## 3. 데이터 모델

```
account(1) ───< (N)verification_challenge
                       │ account_id FK (NOT credential FK — 증명 주체는 사람)
```

`VerificationChallenge` 필드:

| 필드 | 타입 | null | 의미 |
|---|---|---|---|
| id | Long | PK | IDENTITY |
| account | @ManyToOne(LAZY) | NOT NULL | 증명 주체 |
| channel | VerificationChannel | NOT NULL | 발송 채널 |
| purpose | VerificationPurpose | NOT NULL | 목적(가입검증/링킹) |
| codeHash | String | NOT NULL | 코드 **해시** (원문 저장 금지) |
| status | VerificationStatus | NOT NULL | PENDING으로 시작 |
| attemptCount | int | NOT NULL | 검증 실패 누적(기본 0) |
| lastAttemptAt | Instant | NULL | 마지막 시도 시각(1초 throttle용) |
| expiresAt | Instant | NOT NULL | 만료 절대시각(UTC) |
| consumedAt | Instant | NULL | CONSUMED된 시각(감사용) |
| createdAt | Instant | NOT NULL | 발급 시각 |

- 모든 시간은 **`Instant`(UTC)** — 타임존 버그 차단.
- 유니크 제약 없음(재발송으로 여러 행 정상).
- 조회 인덱스 `(account_id, channel, purpose, status)`, 정리잡용 `expires_at` 인덱스(T7).

## 4. 상태 머신 (엔티티 불변식)

```
                    issue()
                       │
                       ▼
   재발송/issue ──► [ PENDING ] ──── consume() (코드 일치) ──► [ CONSUMED ]
   (정책1)            │  │  ▲                                   consumedAt 기록
   기존 것 supersede  │  │  └─ recordFailure() (코드 불일치, attemptCount++)
                      │  │
   attemptCount>=5 ───┘  └─ supersede() ──► [ SUPERSEDED ]
   또는 형제 성공(정책2)                       (안 쓰이고 폐기)

[ CONSUMED ] / [ SUPERSEDED ] = 종료 상태. consume() 재호출 시 거부.
EXPIRED는 별도 상태 아님 — expiresAt 로 유도, isUsable()이 시간으로 판정.
```

엔티티 메서드 (상태 전이는 오직 이 메서드로, 세터 노출 금지):

```
boolean isUsable(Instant now)   = status==PENDING && now < expiresAt && !isLocked()
boolean isLocked()              = attemptCount >= MAX_ATTEMPTS(5)
void    consume(Instant now)    PENDING 아니거나 만료면 throw → CONSUMED + consumedAt
void    recordFailure(Instant now)  attemptCount++, lastAttemptAt=now
void    supersede()             PENDING 일 때만 → SUPERSEDED
```

생성: 정적 팩토리 `issue(account, channel, purpose, codeHash, expiresAt)` (status=PENDING),
private 생성자 + `@NoArgsConstructor(PROTECTED)`. codeHash는 이미 해시된 값만 받음
(엔티티는 원문 코드도 PasswordEncoder도 모름).

## 5. OTP 무차별 대입 방어 (★보안 핵심)

6자리 숫자 = 10^6. **시간 + 최대횟수 이중 방어**:

```
verify(account, channel, purpose, rawCode):
  1) PENDING 챌린지 조회 (없으면 실패)
  2) !isUsable(now)            → 실패 (만료/종료상태)
  3) isLocked() (attempt>=5)   → supersede() + 실패  (재발송 강제)
  4) lastAttemptAt != null && (now - lastAttemptAt) < 1초
                               → 실패 "too fast"  (※ attemptCount 안 깎음)
  5) PasswordEncoder.matches(rawCode, codeHash)?
       불일치 → recordFailure(now) + 실패
       일치   → consume(now)
                + SIGNUP_VERIFY면 emailVerified 반영 (§7)
                + 같은 키의 남은 PENDING 형제 supersede (정책2)
```

- **5회 한도**가 본질 방어(코드당 추측 5번 → 사실상 뚫기 불가).
- **1초 throttle**은 스크립트 연사 차단 보조. throttle 거부는 *추측이 아니므로* 횟수 미차감
  (정상 사용자 더블클릭 보호).

## 6. 채널 추상화 — VerificationStrategy

```java
interface VerificationStrategy {
    VerificationChannel channel();              // 담당 채널
    void send(String destination, String rawCode);
}
```

- `EmailVerificationStrategy` (channel=EMAIL): 실구현. v1은 SMTP 미설정 시 **코드를 로그로 출력하는
  dev 모드**(운영 전환 시 이 로그 라인 제거 — TODO). JavaMailSender 주입.
- `KakaoMsgVerificationStrategy` / `SmsVerificationStrategy`: **스텁**. `channel()`만 반환,
  `send()`는 `throw new UnsupportedOperationException("not implemented in v1")` + 연동 위치 주석.
- 디스패치: Spring이 `List<VerificationStrategy>` 주입 → `channel()` 키로 `Map` 구성.
  새 채널 = 인터페이스 구현만 추가(OCP).

## 7. VerificationService — 글루

`Clock` 주입(`clock.instant()`로 now 획득 → 테스트에서 `Clock.fixed`로 고정).

```
issue(account, channel, purpose):
  1) 기존 PENDING(account,channel,purpose) 전부 supersede()        // 정책1
  2) rawCode = 난수 6자리   (메모리에만)
  3) codeHash = passwordEncoder.encode(rawCode)
  4) challenge = VerificationChallenge.issue(.., expiresAt = now + TTL)  저장
  5) publish VerificationIssuedEvent(challengeId, destination, rawCode)  // §8

verify(...): §5 흐름
```

**emailVerified 매핑 (§발견2-A):** SIGNUP_VERIFY 성공 시,
`credentialRepository.findByAccountAndType(account, EMAIL_PASSWORD)` 로 credential을 찾아
`credential.markEmailVerified()`. (챌린지에 credential FK 안 둠 — account 모델 유지.)

## 8. 발송은 커밋 이후 (§발견3-A)

```
issue() ──저장+이벤트발행──► [트랜잭션 커밋] ──AFTER_COMMIT──► 리스너 ──► strategy.send()
                                                              (rawCode 메모리 전달)
```

- `@TransactionalEventListener(phase = AFTER_COMMIT)` 로 **저장 확정된 코드만 발송**.
- rawCode는 이벤트 객체로만 흐르고 **영속 안 됨**, 요청 수명 동안만 메모리 체류.
- 트레이드오프: 커밋 후 send 실패 → "챌린지 있는데 메일 안 감" → 사용자 **재발송**으로 복구
  (at-most-once 허용).

## 9. Repository

```java
// VerificationChallengeRepository
List<VerificationChallenge> findByAccountAndChannelAndPurposeAndStatus(
    Account account, VerificationChannel channel,
    VerificationPurpose purpose, VerificationStatus status);

// CredentialRepository (추가)
Optional<Credential> findByAccountAndType(Account account, CredentialType type);
```
- issue의 "기존 PENDING supersede", verify의 "PENDING 매칭/형제 supersede" 둘 다 첫 쿼리로 커버.
- 평소 PENDING 0~1개라 루프 처리 OK. 추후 `@Modifying` 벌크 옵션(성능 발견6).

## 10. 보안/설계 원칙

1. 원문 코드 절대 미저장 — codeHash만.
2. 상태 전이는 엔티티 메서드로만(세터 금지) — 불변식 보유.
3. 시간 `Instant`(UTC) + `Clock` 주입.
4. 무차별 대입 = attemptCount 5 + 1초 throttle.
5. 발송은 AFTER_COMMIT.
6. dev 코드 로그는 운영 전 제거.

## 11. 테스트 계획 (구현과 동시 작성, 목표 100%)

엔티티(단위):
- consume: PENDING→CONSUMED + consumedAt
- consume: CONSUMED/SUPERSEDED/만료면 거부
- recordFailure: attemptCount++ , 5회→isLocked()
- 1초 throttle: 간격 미달 거부, 횟수 미차감
- supersede: PENDING만 전이

서비스(단위+통합):
- issue: 기존 PENDING supersede 후 새 발급(정책1)
- issue: AFTER_COMMIT 이벤트로 send 호출됨 [통합]
- verify 성공: consume + emailVerified=true(findByAccountAndType)
- verify: 형제 supersede(정책2)
- verify 틀린코드: attemptCount 증가
- verify 5회 초과: supersede + 실패
- 채널 디스패치: EMAIL→EmailStrategy, KAKAO_MSG/SMS→UnsupportedOperationException

결정론: `Clock.fixed`. 실DB 제약·인덱스 검증: **T6 zonky**.

**★CRITICAL (협상불가):**
1. CONSUMED 코드 재제시 → 거부
2. 만료 코드 → 거부
3. 5회 초과 → 무차별 차단(폐기)

## 12. NOT in scope (v1)

- 카카오/SMS 실발송(스텁만), penny-drop
- 서비스단 account/IP rate limit (attemptCount로 1차 방어, 인프라 보강은 나중)
- 실 SMTP 신뢰성 강화(재시도 큐) — AFTER_COMMIT까지만
- 폰번호 컬럼 (T3 SMS 채널에서)

## 13. 권장 구현 순서

```
1) enum 3개
2) VerificationChallenge 엔티티 (+ 팩토리/불변식/상태머신) + 단위테스트
3) VerificationChallengeRepository + CredentialRepository.findByAccountAndType
4) Clock 빈, PasswordEncoder 빈 확인
5) VerificationStrategy + EmailVerificationStrategy(dev 로그) + 스텁 2개
6) VerificationService.issue/verify + VerificationIssuedEvent + AFTER_COMMIT 리스너
7) ★CRITICAL 테스트 + 나머지 커버리지
```

구현 후 `검증해줘` → 이 문서 기준으로 리뷰.
