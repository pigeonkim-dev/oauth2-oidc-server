# T3 — LinkingService 설계문서 (계정 연결 + pre-hijacking 차단)

> 상태: 설계 확정, 구현 대기. 선행: T1(도메인)·T2(검증) 완료.
> 패키지: `...service` (LinkingService), 도메인은 T1 재사용.
> 간판: SAS가 안 주는 "한 사람의 여러 로그인 수단을 안전하게 묶기".

## 1. 위협 모델 — account pre-hijacking
```
1) 공격자가 victim@x.com 으로 EMAIL_PASSWORD 가입, 이메일 검증 안 함(email_verified=false)
2) 주인이 카카오 로그인, 카카오가 victim@x.com 반환
3) 시스템이 이메일 일치로 기존(공격자) 계정에 카카오 자동연결
   → 주인 신원이 공격자 계정에 붙음 → 공격자가 주인 행세 ☠️
```

## 2. 잠근 규칙
- **R1. 이메일 매칭 금지.** 어떤 프로바이더가 준 이메일로도 기존 계정을 자동 매칭/연결하지 않는다. (pre-hijacking을 근원에서 제거)
- **R2. 양끝 증명.** 연결엔 (a) 새 수단 증명 + (b) 기존 계정 증명이 둘 다 필요.
- **R3. 명시적 연결.** 자동 아님. 사용자가 "이 계정에 연결" 의사를 밝혀야.
- **R4. 인증 후 일괄 생성(D3).** 증명 끝나기 전엔 아무 영속 레코드도 안 만든다. 통과 후 credential·연결을 그때 생성.
- **R5. 증명은 채널로 흐른다.** 각 수단은 자기 채널로 증명(EMAIL→이메일코드, KAKAO→OAuth/카톡), 남의 채널(이메일) 빌려 매칭 안 함.

## 3. 증명 매트릭스 (어떻게 증명하나)
| 수단 | 새 수단 증명 | 비고 |
|---|---|---|
| 기존 계정 (연결 대상) | **LINK 챌린지**(D1b) — 기존 계정의 검증된 채널로 코드 발송 → 입력 | T2 `VerificationPurpose.LINK` 재사용 |
| EMAIL_PASSWORD 추가 | 이메일 코드 (SIGNUP_VERIFY/LINK) | 직접 SMTP |
| KAKAO 추가 | **OAuth 핸드셰이크 (가)** | v1. OAuth가 "그 카카오 통제" 증명 |
| (미래) KAKAO 카톡검증 | KAKAO_MSG 전략 | 스텁 → 실구현 교체점. 비즈 승인 필요 |
| (미래) SMS/FB/IG | SMS 전략 / OAuth-only | FB·IG는 채널검증 불가 → OAuth 베이스라인만 |

> 베이스라인 = **OAuth 핸드셰이크(모든 프로바이더 보편)**. 채널 네이티브 검증(`VerificationStrategy`)은 가능한 채널만 선택 강화. 이게 결정 #1b의 이유.

## 4. 모델 — 별도 링크 테이블 없음
Account 1:N Credential 이므로 **"연결" = 새 Credential 의 `account` FK 를 기존 Account 로 지정.** (T1에서 email 단독 unique 를 뺀 게 이 포석.) 같은 이메일이 EMAIL_PASSWORD + KAKAO 로 한 Account 아래 공존 가능.

## 5. 흐름

### 5-1. 소셜 로그인 디스패치 (이메일 매칭 없음)
```
카카오 OAuth 성공 (provider=kakao, provider_uid 확보) →
  credentialRepo.findByProviderAndProviderUid(kakao, uid) 존재?
    YES → 그 Account 로 로그인 (이미 연결됨)
    NO  → 이메일 매칭 안 함(R1)!
          → 신규로 간주. 두 갈래를 사용자에게:
              · "새 계정으로 시작"  → 새 Account + KAKAO credential 생성
              · "기존 계정에 연결"  → 5-2 명시적 링크 플로우
```

### 5-2. 명시적 링크 플로우 (양끝 증명)
```
[전제] 새 수단은 이미 증명됨 (예: 방금 카카오 OAuth 통과 → kakao 신원 보유)
       이 '증명된 새 수단'을 in-flight 로 들고 있음(아직 미저장, R4)

1) 사용자가 기존 계정 식별자(예: 기존 이메일) 명시
2) 그 식별자의 '검증된' credential 찾기:
     credentialRepo.findByEmailAndType(email, EMAIL_PASSWORD), email_verified=true 인가?
       아니면 → 링크 거부 (증명할 채널 없음 / 미검증 계정)   ← pre-hijacking 차단점
3) 기존 계정 증명: verificationService.issue(existingAccount, EMAIL, LINK, email)  → 코드 발송
4) 사용자가 코드 입력 → verificationService.verify(existingAccount, EMAIL, LINK, code)
       false → 거부
       true  → 5) 로
5) [D3] 이제 모든 것 생성:
     KAKAO credential 을 existingAccount 에 붙여 생성 (Credential.kakao(existingAccount, ...)) + save
     → 연결 완료
```

### LinkingService 시그니처(안)
```java
// 기존 계정 증명 개시 (LINK 코드 발송). 매칭은 사용자가 명시한 식별자 기준, 미검증이면 거부.
void initiateLink(String existingAccountEmail, PendingCredential proven);

// LINK 코드 검증 통과 시 in-flight 증명수단을 기존 계정에 attach (R4: 여기서 생성)
void confirmLink(String existingAccountEmail, String linkCode, PendingCredential proven);
```
- `PendingCredential` = 증명됐지만 아직 미저장인 새 수단(예: kakao provider+uid). in-flight 보관.

## 6. in-flight 상태 보관 (결정 필요 — 웹 레이어 때 확정)
"증명된 새 수단"을 발급~확인 사이에 어디 둘지:
- v1(단일 인스턴스): **HTTP 세션**에 PendingCredential 보관 — 단순.
- 다중 인스턴스: 세션 공유(Spring Session+Redis) 또는 단기 `pending_link` DB 행. (← 멀티서버 확장 시. 메모리 상태 금지 원칙.)
- v1은 세션, 확장점만 열어둠.

## 7. pre-hijacking 차단점 (코드에서 절대 안 하는 것)
- `findByEmail`로 **자동 매칭/연결 안 함** (R1). 이메일은 매칭 키가 아님.
- 기존 계정 연결은 **반드시 LINK 챌린지 통과** 후에만 (R2b).
- **미검증 계정(email_verified=false)** 으로는 링크 대상이 못 됨 (증명 발송할 검증 채널이 없음).
- 증명 전 어떤 credential/연결도 **생성 안 함** (R4).

## 8. ★CRITICAL 테스트 (협상 불가)
1. **pre-hijacking**: 미검증 이메일 계정엔 카카오 자동연결/링크 **불가**
2. **증명 없는 링크 거부**: LINK 코드 검증 안 됐는데 attach 시도 → 거부
3. **정상 링크**: 새 수단 증명(OAuth) + 기존계정 증명(LINK 코드) → 연결 성공 + Account 1개에 credential 2개

## 9. v1 / NOT in scope
- v1: EMAIL 기존계정 증명(LINK 코드) + KAKAO 새수단 증명(OAuth). 실증.
- NOT: 카톡 메시지 실발송(스텁), SMS, FB·IG 프로바이더, 다중 인스턴스 세션 공유(세션으로 v1).

## 10. 권장 구현 순서
```
1) (web 전이라면) 카카오 OAuth 클라이언트 설정 확인 — 이미 oauth2-client 의존성 있음
2) LinkingService.confirmLink 핵심: verify(LINK) → Credential.kakao(existingAccount,...) attach
3) initiateLink: 식별자→검증된 credential 찾고 issue(LINK). 미검증이면 거부.
4) 디스패치(5-1): findByProviderAndProviderUid 분기
5) ★CRITICAL 3개 테스트 (서비스 단위, Mockito)
6) in-flight 보관은 web 레이어에서 세션으로
```
