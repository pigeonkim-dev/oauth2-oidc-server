package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.RefreshToken;
import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, String> {   // PK = jti(String)

    List<RefreshToken> findByFamily(RefreshTokenFamily family);
}
