package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.RefreshTokenFamily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenFamilyRepository
        extends JpaRepository<RefreshTokenFamily, Long> {

    @Modifying
    @Query("delete from RefreshTokenFamily f where not exists (select 1 from RefreshToken t where t.family = f)")
    int deleteEmptyFamilies();
}
