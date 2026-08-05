package com.pigeonkim.oauth2authorizationserver.service;

import com.pigeonkim.oauth2authorizationserver.domain.Credential;
import com.pigeonkim.oauth2authorizationserver.domain.CredentialType;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CredentialUserDetailsService implements UserDetailsService {

    private final CredentialRepository credentialRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<Credential> credential = credentialRepo.findByEmailAndType(email, CredentialType.EMAIL_PASSWORD);

        if (credential.isEmpty()) {
            throw new UsernameNotFoundException("not found");
        }

        return User.withUsername(credential.get().getEmail())
                .password(credential.get().getPasswordHash())
                .authorities("ROLE_USER")
                .disabled(!credential.get().isEmailVerified())
                .build();
    }
}
