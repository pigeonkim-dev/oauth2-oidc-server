package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamily;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 첫 DB 통합 테스트 — 배관이 실제로 도는지 확인하는 것이 1차 목적이다.
 * <p>
 * 이 테스트가 통과하면 아래가 전부 증명된다.
 * <ol>
 *   <li>테스트가 개발 DB 가 아니라 oauth2_idp_test 를 본다</li>
 *   <li>Flyway 가 테스트 DB 에도 V1 을 적용했다</li>
 *   <li>ddl-auto=validate 가 통과했다 = Flyway 스키마와 엔티티가 일치한다</li>
 *   <li>리포지토리가 실제 Postgres 에 읽고 쓴다</li>
 * </ol>
 * <p>
 * ★ 애노테이션 세 줄이 각각 하는 일
 * <ul>
 *   <li><b>@DataJpaTest</b> — JPA 관련 빈만 올리는 '슬라이스'. 컨트롤러·시큐리티·메일은 안 뜬다.
 *       전체 컨텍스트(@SpringBootTest)보다 훨씬 빠르고, 없는 설정 때문에 실패할 일도 적다.
 *       기본적으로 각 테스트를 트랜잭션으로 감싸고 끝나면 <b>롤백</b>한다 → 테스트끼리 안 더럽힌다.</li>
 *   <li><b>@AutoConfigureTestDatabase(replace = NONE)</b> — ⚠️ 이게 없으면 @DataJpaTest 가
 *       우리 DataSource 를 <b>임베디드 DB 로 갈아치운다</b>. 그러면 Postgres 가 아니라 H2 를 테스트하게 되고,
 *       방언 차이 때문에 "테스트는 통과하는데 운영에서 깨지는" 최악의 상황이 된다.
 *       NONE = "바꾸지 말고 내가 설정한 것을 그대로 써라".</li>
 *   <li><b>@ActiveProfiles("test")</b> — application-test.yml 을 읽게 한다.
 *       이걸 빼면 default 프로파일(local)이 살아나 <b>개발 DB 를 건드린다</b>.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RefreshTokenFamilyRepositoryTest {

    @Autowired
    private RefreshTokenFamilyRepository familyRepo;

    /**
     * ★ @DataJpaTest 가 자동으로 올려주는 테스트 전용 EntityManager 래퍼.
     * 여기서 필요한 건 딱 두 개다.
     * <ul>
     *   <li>{@code em.flush()} — 쌓아둔 INSERT/UPDATE 를 지금 DB 로 내보낸다 (커밋은 아니다)</li>
     *   <li>{@code em.clear()} — 영속성 컨텍스트(1차 캐시)를 비운다 → 다음 조회는 진짜 SELECT 가 나간다</li>
     * </ul>
     * flush 없이 clear 하면 아직 DB 로 안 나간 INSERT 가 통째로 버려진다. 순서가 중요하다.
     */
    @Autowired
    private TestEntityManager em;

    // Instant 는 Instant.now() 대신 고정값 — 도메인 규칙(Clock 주입)과 결이 같다.
    // 나노초가 0 이라 Postgres timestamp(6) 왕복에도 값이 안 잘린다.
    private static final Instant NOW = Instant.parse("2026-08-27T10:30:00Z");

    @Test
    void 저장한_패밀리를_id로_다시_읽으면_같은_값() {

        RefreshTokenFamily family = RefreshTokenFamily.start(1L, "TEST", NOW);
        familyRepo.save(family);
        familyRepo.flush();
        em.clear();

        RefreshTokenFamily found = familyRepo.findById(family.getId())
                .orElseThrow(AssertionError::new);

        assertEquals(family.getAccountId(), found.getAccountId());
        assertEquals(family.getClientId(), found.getClientId());
        assertEquals(family.getStatus(), found.getStatus());
        assertEquals(family.getCreatedAt(), found.getCreatedAt());
    }
}
