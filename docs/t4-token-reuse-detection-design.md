# T4 — Refresh 토큰 reuse-detection 설계문서 (초안)

> 상태: **초안**(플랜 기반, 구현 전 T1~T3처럼 정제 예정). 선행: T1~T3.
> 간판 시연: "CONSUMED refresh 재제시 → 패밀리 전체 무효".

## 1. 목표
refresh 토큰 회전(rotation) + 도난 탐지. 한 번 쓴(rotated) refresh가 다시 제시되면 = 도난 신호로 보고 **그 패밀리 전체를 무효화** (OAuth 2.0 Security BCP).

## 2. 모델
```
refresh_token_family(id, account_id, client_id, status[ACTIVE|REVOKED], created_at)
   1───N
refresh_token(jti PK, family_id FK, status[ACTIVE|CONSUMED|REVOKED],
              issued_at, expires_at)
인덱스: refresh_token(jti)
```
- **패밀리** = 한 로그인 세션의 회전 체인. 회전할 때마다 같은 family 아래 새 토큰.
- status 패턴은 T2 VerificationStatus와 동형(영속 상태 머신) — 일관.

## 3. 핵심 흐름
```
발급:     family 생성(ACTIVE) + refresh_token #1 (ACTIVE)
회전:     refresh #1 제시 → #1 CONSUMED + refresh #2 ACTIVE 발급
재사용:   이미 CONSUMED 된 #1 다시 제시
            → 도난 탐지 → family.status=REVOKED → family의 모든 refresh REVOKE
            → 이후 그 패밀리 토큰 전부 무효 (재로그인 강제)
```

## 4. 상태 전이 (엔티티 불변식 — T2 습관)
```
refresh_token: ACTIVE ──rotate──> CONSUMED
                  └──reuse/family revoke──> REVOKED
family:        ACTIVE ──reuse detected──> REVOKED
```
- 전이는 엔티티 메서드로만(consume/revoke), 세터 금지.

## 5. SAS 연동
- SAS의 `OAuth2AuthorizationService`가 토큰 저장을 관할 → 우리 reuse-detection을 어디 끼울지 결정 필요:
  - 커스텀 `OAuth2AuthorizationService`(JDBC 기반 확장) 또는 토큰 엔드포인트 필터/커스터마이저에서 회전 시 우리 family/row 갱신.
- ⚠️ 멀티 인스턴스: in-memory authorization service면 깨짐 → **JdbcOAuth2AuthorizationService** 공유 필요(개요 §멀티서버).

## 6. 동시성 (정제 포인트)
같은 refresh가 두 요청에 동시 도착 → "CONSUMED 표시 + 새 발급"을 **DB 원자적**으로(낙관적 락 @Version 또는 unique 제약)해야 reuse-detection 정확. T2 verify의 "최신만/레이스" 사고와 같은 계열.

## 7. ★CRITICAL 테스트 (협상 불가)
1. **CONSUMED refresh 재제시 → family REVOKED, 그 패밀리 전부 무효** (간판)
2. 정상 회전: ACTIVE → CONSUMED + 새 ACTIVE
3. REVOKED family 토큰으로는 접근 불가

## 8. NOT in scope (v1)
- 멀티 클라이언트별 정교한 정책, 디바이스 바인딩(백로그 참조)

## 9. 정제 시 결정할 것
- reuse-detection 끼우는 위치(커스텀 AuthorizationService vs 필터)
- refresh 저장을 SAS 기본 테이블에 얹을지, 우리 family/row를 별도로 둘지
- 동시성 메커니즘(@Version vs unique 제약)
