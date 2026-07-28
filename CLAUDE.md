# CLAUDE.md

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
- **Spring Authorization Server is merged into `spring-security-config` 7.1.0** (Boot 4.1 pulls SAS via Security 7, not the standalone 1.x artifact). Two gotchas vs every online SAS 1.x / Boot-3 example:
  - `OAuth2AuthorizationServerConfigurer` moved to `org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer` (NOT the old `org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.*`).
  - There is **no static `authorizationServer()` factory** in this version — construct it with `new OAuth2AuthorizationServerConfigurer()`, then use `http.with(configurer, customizer)`. `getEndpointsMatcher()`, `.oidc(...)` are instance methods on it.
- **SAS internals reference (extracted from the 7.1.0 sources jar — don't re-spelunk):** to see how SAS persists authorizations, read `JdbcOAuth2AuthorizationService` (sources jar at `~/.gradle/caches/.../spring-security-oauth2-authorization-server/7.1.0/<hash>/...-sources.jar`). Key facts for hand-writing a custom `OAuth2AuthorizationService` (e.g. the JPA impl):
  - **JSON (attributes/metadata) uses Jackson 3 now**, not Jackson 2. Build the mapper with `JsonMapper.builder().addModules(SecurityJacksonModules.getModules(classLoader)).build()` (`tools.jackson.databind.json.JsonMapper` + `org.springframework.security.jackson.SecurityJacksonModules`). A plain `ObjectMapper` will fail to (de)serialize the security types. Read a map back via `jsonMapper.readValue(json, jsonMapper.getTypeFactory().constructType(new ParameterizedTypeReference<Map<String,Object>>(){}.getType()))`.
  - Conversion lives in two inner mappers: **read** = `AbstractOAuth2AuthorizationRowMapper.mapRow` (rebuilds via `OAuth2Authorization.withRegisteredClient(rc).id()/.principalName()/.authorizationGrantType()/.authorizedScopes()/.attributes()` then `.token(tokenObj, md -> md.putAll(metadata))` per token); **write** = `AbstractOAuth2AuthorizationParametersMapper.apply` (pulls `authorization.getToken(OAuth2AuthorizationCode.class)`, `.getToken(OAuth2AccessToken.class)`, `.getToken(OidcIdToken.class)`, `.getRefreshToken()`; each `Token<T>` gives `.getToken().getTokenValue()/.getIssuedAt()/.getExpiresAt()` + `.getMetadata()`).
  - `findByToken(token, tokenType)` routing: `tokenType == null` → search all token columns; else match on `tokenType.getValue()` == `OAuth2ParameterNames.STATE`/`.CODE`/`OidcParameterNames.ID_TOKEN`, or `tokenType.equals(OAuth2TokenType.ACCESS_TOKEN)`/`.REFRESH_TOKEN`.
  - `state` is stored in its own column but lives in the authorization's attributes (`getAttribute(OAuth2ParameterNames.STATE)`); OIDC id-token claims are stored inside its metadata under `OAuth2Authorization.Token.CLAIMS_METADATA_NAME`. `user_code`/`device_code` columns are Device-flow only — skippable if you don't use that grant.
- **SAS refresh-grant internals (for T4 reuse-detection wiring — don't re-spelunk `OAuth2RefreshTokenAuthenticationProvider`):** the refresh grant is handled by `OAuth2RefreshTokenAuthenticationProvider.authenticate()`. Order: (1) `findByToken(presentedRefreshValue, REFRESH_TOKEN)` → **null ⇒ immediate `invalid_grant`**; (2) `refreshToken.isActive()` check; (3) generate access token; (4) **if `!registeredClient.getTokenSettings().isReuseRefreshTokens()`** → generate a NEW refresh token and `authorizationBuilder.refreshToken(new)` **replaces the old value**; (5) `authorizationService.save(authorization)` **overwrites our DB refresh-token column with the new value** — so the OLD refresh value is gone from SAS's store; presenting it again just 404s at step 1. Consequences for T4: (a) reuse of an already-rotated token can NOT be caught by SAS or by our `OAuth2AuthorizationService.save/findByToken` — SAS erased the trail, so **our own ledger (`refresh_token`/`refresh_token_family`) must be the source of truth for reuse**; (b) the class is **`final`** ⇒ cannot subclass — wrap it by **delegation**. Wiring decision (locked T4 §9): a `ReuseDetecting...Provider implements AuthenticationProvider` that, BEFORE delegating, looks up the presented value in our ledger (CONSUMED ⇒ revoke family + throw `invalid_grant`), delegates to the real provider (does the rotation), then AFTER success calls `refreshTokenService.rotate(presentedValue, newValue, ...)`. Register/replace it via `OAuth2AuthorizationServerConfigurer.tokenEndpoint(t -> t.authenticationProviders(list -> ...))` (the consumer exposes SAS's default provider list so you can find + replace the default refresh provider). Use the opaque refresh-token **value as the jti/PK**. **Still open:** recording the FIRST refresh token at initial issuance (auth-code grant is a separate `final` provider) — see T4 doc.

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
