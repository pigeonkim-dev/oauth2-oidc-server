# CLAUDE.md

##사용자 스타일
- 1년자 자바 개발자 신입 수준
- C# .netframework 4.8, ASP.NET Webforms, ASP.NET MVC 유경험자 12년차 웹개발자
- 프론트 엔드 조금 알고 있음
- JAVA BCL 많이 모른다.
- Spring, Spring Boot 배우는 중
- Spring JPA 배우는 중
- JAVA/Spring Boot 기반 웹개발 배우는 중

## 코드 작업 스타일 설명
- 코드는 전체 코드를 만들어서 제공 하지 말것
- 클래스는 클래스의 껍데기만 제공
- 변수는 변수 자료형이랑 이름만 제공
- 메서드는 메서드의 리턴타입과 시그니처 입력값에 대한 설명만 제공
- 어노테이션이 필요 하면 클래스, 변수, 메서드에 필요한 어노테이션이 어떤 것이 있고 어떤 역할인지 설명 하고 제공
- 특별히 요청시 전체 클래스로 제공 하고 코멘트를 써놓는다.


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A **central-account Identity Provider (IdP)** built on **Spring Authorization Server (SAS)**. The thesis of the project is to hand-build the seams SAS leaves blank: the **Account/Credential domain, account linking, channel verification, and refresh-token reuse detection**. Treat it as production code (it is private for reuse, not a throwaway portfolio).

The single source of truth for design is **`docs/`** — start with [`docs/00-overview-design.md`](docs/00-overview-design.md) (master index: build order, locked decisions, full data model, per-task design docs, security backlog). Per-feature docs (`t1`..`t8`) carry the rationale and the locked invariants; read the relevant one before touching that area.

## Stack quirks (will bite if forgotten)

- **Spring Boot 4.1.0** (bleeding edge) + Java 17, Gradle (Groovy). Base package `com.pigeonkim.oauth2authorizationserver` (no underscores).
- Boot 4 renamed/moved things vs the Boot-3 docs most examples assume:
  - starter is `spring-boot-starter-webmvc` (not `-web`); test starters are per-module (`spring-boot-starter-data-jpa-test`, etc.).
  - Test-slice annotations moved: `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`; `@AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` (NOT the old `...boot.test.autoconfigure.*`).
- `@Transactional` should be the Spring one (`org.springframework.transaction.annotation.Transactional`), not `jakarta.transaction.*`.

## Commands

```bash
./gradlew compileJava                                  # compile main
./gradlew test                                         # all tests
./gradlew test --tests "*VerificationServiceTest"      # single test class
./gradlew build                                        # full build
```

- The app is **not runnable yet**: no DataSource / SAS config is wired (`application.yaml` only sets the app name). `bootRun` and any full-context `@SpringBootTest` will fail until a Postgres datasource exists. Run **unit tests by filter**, not the whole suite, until then.
- Integration testing (real Postgres via zonky) is **deferred** — zonky 2.6.0 is incompatible with Boot 4.1 (drags in Boot 3.5 test-autoconfigure, `MissingProviderDependencyException` on the ZONKY provider). See `docs/00-overview-design.md` §6 and the T6 note before retrying.

## Architecture & cross-cutting invariants

Layered packages under the base package: `config, domain, repository, service, web, dto, exception`. `web/` and `dto/` are not built yet.

These invariants span multiple files and the design docs — **do not break them**:

- **Account (person) ↔ Credential (proof), 1:N.** "Linking" = setting a Credential's `account` FK to an existing Account; there is intentionally **no link table** and **no single-column unique on `email`** (so the same email can exist as both EMAIL_PASSWORD and KAKAO under one account).
- **Credential is single-table STI** (one class + `type` enum, not JPA `@Inheritance`). The conditional-NOT-NULL gaps are enforced in the app, two ways: **static factories** (`Credential.basic()` / `Credential.kakao()`, private ctor, no `@Builder`) + an **`@AssertTrue` invariant** that Hibernate runs on flush. Never add a public all-args constructor or `@Builder` that lets callers build an inconsistent Credential.
- **All times are `Instant` (UTC); inject `java.time.Clock`** and call `clock.instant()`. Do not call `Instant.now()` in domain/service code — tests rely on `Clock.fixed(...)` for determinism. (T1 `createdAt` is still `LocalDateTime` — a known inconsistency to unify, not a pattern to copy.)
- **Verification codes**: store `codeHash` only (BCrypt via the shared `PasswordEncoder`), never the raw code; never log it outside the EMAIL dev-stub. Brute-force defense = `attemptCount` max 5 + 1-second throttle (throttle rejection does not burn an attempt). `verify()` queries PENDING **ordered newest-first, verifies only the latest, and supersedes the rest** — multiple PENDING are expected (flaky-network resends + races), so never assume exactly one.
- **Email is sent only after commit**: `issue()` publishes a `VerificationIssuedEvent` (raw code carried in memory only); a `@TransactionalEventListener(AFTER_COMMIT)` dispatches to the channel `VerificationStrategy`. EMAIL is real (dev = log); KAKAO_MSG/SMS are intentional stubs that `throw UnsupportedOperationException`.
- **Account linking blocks pre-hijacking**: NEVER auto-match accounts by a provider-supplied email (no `findByEmail` to link). Linking requires proof on both ends — the existing account via a `LINK` verification challenge, the new credential via its own channel (kakao = OAuth handshake in v1) — and records are created **only after** both proofs pass.
- **Keep state in the DB, not in memory** (the design targets horizontal scaling). When SAS is wired, the `OAuth2AuthorizationService` and signing keys must be shared/persistent, and `@Scheduled` jobs must run on one instance (ShedLock/leader).

## Test conventions

- **Domain/entity logic** → plain JUnit, **no Spring annotations**, no Mockito infra; construct via the factory and assert (mock `Account` only as a placeholder reference). Example: `VerificationChallengeTest`.
- **Service logic** → `@ExtendWith(MockitoExtension.class)` with `@Mock` collaborators; inject a real `Clock.fixed(...)` (not a mock) by constructing the service in `@BeforeEach`. Example: `VerificationServiceTest`.
- **Never put pure-logic tests in a `@SpringBootTest`** class — it boots the full context and fails on the missing DataSource.
- Aim for the listed **★CRITICAL tests** in each design doc (e.g. reuse-detection family revoke, pre-hijacking refusal) — those are non-negotiable.

## Workflow

- Branches: `develop` (working) and `main` (stable). Commit to `develop`. Git identity is repo-local (`redtigerkim`).
- Secrets go in gitignored `application-local.yml` / `application-secret.yml` / `*.env` — never in `application.yaml`.
