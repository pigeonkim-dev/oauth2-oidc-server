package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenFamilyRepository
        extends JpaRepository<RefreshTokenFamily, Long> {
}
