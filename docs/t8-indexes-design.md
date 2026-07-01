# T8 — 인덱스 설계문서 (초안)

> 상태: **초안**(플랜 기반). 마무리 성격(다른 태스크가 만든 쿼리에 맞춰 확정).

## 1. 목표
실제 조회 패턴에 맞는 인덱스로 성능 확보. 추측 말고 **각 태스크의 쿼리에서 역산**.

## 2. 현황 (이미 있는 것)
- `credential`: UNIQUE(type, email), UNIQUE(provider, provider_uid) — unique는 인덱스도 겸함
- `verification_challenge`: idx_vc_lookup(account_id, channel, purpose, status), idx_vc_expires(expires_at)

## 3. 추가 후보 (쿼리 기준)
| 인덱스 | 쓰는 쿼리 | 출처 |
|---|---|---|
| credential(email) | findByEmailAndType (email 선두) | 복합 unique(type,email)가 email 선두면 별도 불필요 — 컬럼 순서 확인 |
| credential(account_id) | findByAccountAndType, FK 조회 | T1/T2 |
| refresh_token(jti) | 토큰 검증 조회 | T4 |
| refresh_token(family_id) | 패밀리 무효화 | T4 |
| refresh_token(expires_at, status) | T7 정리잡 | T7 |

## 4. 정제 포인트
- **복합 인덱스 컬럼 순서** = 가장 선택적/선두 조건 우선. unique(type,email)이 있으면 email 단독 조회도 그 인덱스를 (선두면) 탐 → 중복 인덱스 만들지 말 것.
- 부분 인덱스 후보: `verification_challenge ... WHERE status='PENDING'` (다중 PENDING 동시삽입 차단, 개요 §결정5) — Postgres 부분 unique.
- 인덱스는 쓰기 비용도 있으니 **실제 쿼리 있는 것만**. EXPLAIN으로 검증(T6 통합 환경에서).

## 5. 의존
- T4·T7이 만드는 쿼리가 확정돼야 이 인덱스도 확정 → **T8은 T4/T7 뒤 마무리**가 자연스러움.
