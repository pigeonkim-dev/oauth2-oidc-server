/**
 * JPA entities and domain enums — the core of the system.
 * Account (person) and Credential (proof, single-table STI) split,
 * VerificationChallenge, and the refresh-token family/row aggregate.
 * As it grows, split into sub-packages: account, credential, verification, token.
 */
package com.pigeonkim.oauth2_authorization_server.domain;
