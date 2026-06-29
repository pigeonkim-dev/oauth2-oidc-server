/**
 * Spring Data JPA repositories. Persistence access only.
 * Claim-building reads use {@code @EntityGraph} to avoid N+1 on linked credentials.
 */
package com.pigeonkim.oauth2_authorization_server.repository;
