package com.pigeonkim.oauth2authorizationserver.repository;

import com.pigeonkim.oauth2authorizationserver.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
