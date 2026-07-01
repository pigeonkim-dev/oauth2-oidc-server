# T7 — 토큰/챌린지 정리 @Scheduled 설계문서 (초안)

> 상태: **초안**(플랜 기반). 운영 항목.

## 1. 목표
만료·소비된 레코드가 무한히 쌓이지 않게 주기적 정리. 운영 위생.

## 2. 대상
| 테이블 | 삭제 조건 |
|---|---|
| verification_challenge | status in (CONSUMED, SUPERSEDED) 이거나 expires_at < now - 보존기간 |
| refresh_token | status in (CONSUMED, REVOKED) 이거나 expires_at < now - 보존기간 |
| refresh_token_family | 살아있는 토큰 없는 빈 패밀리 |

- 보존기간(예: 만료 후 7~30일)은 **감사/디버깅**을 위해 즉시 삭제 대신 유예. config 값.

## 3. 메커니즘
```java
@Scheduled(cron = "0 0 * * * *")   // 매시 (예시)
@Transactional
public void cleanup() {
    Instant cutoff = clock.instant().minus(RETENTION);
    // 벌크 delete (@Modifying @Query) — 대량이면 배치
}
```
- 시간은 주입된 `Clock` 사용(테스트 결정론).
- 대량 삭제는 한 트랜잭션에 다 넣지 말고 **배치/페이징**(락·WAL 부담).

## 4. ⚠️ 멀티 인스턴스 (개요 §멀티서버)
인스턴스마다 @Scheduled가 돌면 정리가 **동시 N번** 실행. → **ShedLock** 또는 리더 선출로 **한 인스턴스만** 실행. v1 단일 인스턴스면 무관, 확장 시 필수.

## 5. 인덱스 의존
정리 쿼리(`expires_at < ?`, `status in ?`)가 빠르려면 인덱스 필요 → T8.
verification_challenge는 이미 `idx_vc_expires` 있음.

## 6. 테스트
- 만료/소비 레코드가 보존기간 후 삭제, 그 전엔 보존
- 살아있는(ACTIVE/PENDING) 레코드는 안 건드림 (Clock.fixed로 결정론)
