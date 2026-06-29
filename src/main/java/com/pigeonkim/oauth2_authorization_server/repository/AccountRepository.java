package com.pigeonkim.oauth2_authorization_server.repository;

import com.pigeonkim.oauth2_authorization_server.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
