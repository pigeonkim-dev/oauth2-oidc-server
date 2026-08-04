package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.RefreshToken;
import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, String> {   // PK = jti(String)

    List<RefreshToken> findByFamily(RefreshTokenFamily family);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
