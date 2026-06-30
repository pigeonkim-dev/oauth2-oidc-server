# 중앙 계정 IdP — 통합 설계 개요

> 갱신: 2026-06-30. T1·T2 구현 완료, T6 보류, T3 다음.
> 비공개 프로젝트(사업 준비, 배포·운영 경험). 외부엔 배포된 사이트만 공개.

## 0. 프로젝트
- **무엇**: Spring Authorization Server(SAS) 위에 중앙 계정 IdP.
- **스택**: Spring Boot 4.1.0, Java 17, Gradle, PostgreSQL. base pkg `com.pigeonkim.oauth2authorizationserver`.
- **설계 명제**: 프레임워크(SAS)가 비워둔 이음새를 손수 설계 — Account/Credential 도메인, LinkingService, VerificationStrategy, refresh-token reuse-detection.

## 1. 척추 (빌드 순서)
```
② 링킹 모델 결정 → ① 영속화 → ③ 토큰 클레임 → ④ 토큰 방어
정체성 중심: Account(사람) ↔ Credential(증명수단) 분리
```

## 2. 잠근 핵심 결정
| # | 결정 |
|---|---|
| 1 | 링킹 = 채널-적합 증명 + 명시적 연결. 미검증 email 일치 자동연결 **금지**(account pre-hijacking 차단) |
| 2 | Credential = **단일테이블 STI**(single class + type enum), nullable 갭은 **팩토리 + @AssertTrue 이중 강제** |
| 3 | reuse-detection = 영속 토큰행 + status. CONSUMED refresh 재제시 → **패밀리 전체 REVOKE** |
| 4 | 검증코드 = **codeHash만 저장**(BCrypt), Instant(UTC), 5회+1초 무차별 방어, 발송은 AFTER_COMMIT |
| 5 | 다중 PENDING은 필연(끊기는 망 재전송+레이스) → verify는 **최신만 검증, 나머지 폐기** |
| - | 데모 보호 클라이언트 = 더미 SPA |

## 3. 데이터 모델 (전체)
```
account(id, display_name, created_at)
   1───N
credential(id, account_id FK, type[EMAIL_PASSWORD|KAKAO], email(nullable), email_verified,
           password_hash[EMAIL만], provider/provider_uid[KAKAO만], created_at)
   UNIQUE(type,email)  ·  UNIQUE(provider,provider_uid)
verification_challenge(id, account_id FK, channel[EMAIL|KAKAO_MSG|SMS],
           purpose[SIGNUP_VERIFY|LINK], code_hash, status[PENDING|CONSUMED|SUPERSEDED],
           attempt_count, last_attempt_at, expires_at, consumed_at, created_at)
   index(account_id, channel, purpose, status) · index(expires_at)
refresh_token_family / refresh_token (T4)
```

## 4. 레이어/공통
- 패키지: config, domain, repository, service, web, dto, exception (각 package-info.java)
- 공통 빈: `Clock`(systemUTC, 테스트는 Clock.fixed), `PasswordEncoder`(BCrypt)
- 시간: **Instant(UTC)** 표준 (T1 createdAt만 LocalDateTime, 통일 권장)

## 5. 진행 현황
| Task | 내용 | 상태 |
|---|---|---|
| T1 | Account/Credential STI 도메인 | ✅ 구현·단위테스트·커밋 |
| T2 | VerificationStrategy + EMAIL + Challenge | ✅ 구현·단위테스트·커밋 |
| T6 | zonky 통합 테스트 하니스 | ⏸ **보류**(zonky×Boot4.1 호환 — 배포 직전/지원 시 재개) |
| T3 | LinkingService (양끝 증명, pre-hijacking 차단) | ▶ 다음 |
| T4 | refresh 패밀리 + reuse detection | 대기 |
| T5 | OAuth2TokenCustomizer 클레임(@EntityGraph) | 대기 |
| T7 | 토큰 정리 @Scheduled | 대기 |
| T8 | 인덱스 3종 | 대기 |

## 6. 테스트 전략
- **단위**: 엔티티(애너테이션 없음), 서비스(@ExtendWith(MockitoExtension)+Clock.fixed) — 로직
- **통합(T6)**: @DataJpaTest/@SpringBootTest + zonky 임베디드 Postgres — 스키마·제약·@AssertTrue·이벤트·부팅
- Boot4 테스트 슬라이스 패키지: `@DataJpaTest`=`...boot.data.jpa.test.autoconfigure`, `@AutoConfigureTestDatabase`=`...boot.jdbc.test.autoconfigure`

## 7. 문서
- 이 파일 = 개요
- [t1-domain-design.md](t1-domain-design.md) — Account/Credential STI
- [t2-verification-design.md](t2-verification-design.md) — 검증 서브시스템
- (T3~ 작성 예정)
