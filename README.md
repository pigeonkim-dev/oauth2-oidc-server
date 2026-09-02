# OAuth2 / OIDC Authorization Server (구현 중)

Spring Authorization Server 기반으로 자체 OAuth2/OIDC 인증서버를
직접 설계·구현하는 학습·포트폴리오 프로젝트입니다.

## 왜 만드나
전 직장에서 OAuth2 Client(구글·페이스북 소셜 로그인)를 연동해봤고,
서버 쪽을 직접 설계·구현해보기 위해 시작했습니다.

## 핵심 설계 (프레임워크가 비워둔 이음새를 직접 설계)
- Account / Credential 분리 — Account=사람, Credential=증명수단 (단일 테이블 STI)
- 채널-적합 증명 기반 링킹 — 검증 안 된 이메일 일치로 자동 연결 금지 (계정 선점 차단)
- 리프레시 토큰 재사용 탐지 — 재사용 시 토큰 패밀리 전체 무효화

## 기술 스택
- Java 17 · Spring Boot · Spring Authorization Server · Spring Security
- PostgreSQL · Spring Data JPA
- 테스트: JUnit 5 · 통합테스트(zonky embedded Postgres)

## 현재 상태
- [x] 프로젝트 구성 + Account/Credential 도메인 모델
- [x] DB 통합테스트 기반 구축
- [ ] 로그인 → 개인키(RS256) 서명 JWT 발급
- [ ] JWKS 게시(/.well-known/jwks.json)
- [ ] 리프레시 토큰 재사용 탐지

## 개발 방식
AI 코딩 도구(Claude Code)를 활용하되, 아키텍처·도메인 설계·검증은 직접 수행했습니다.