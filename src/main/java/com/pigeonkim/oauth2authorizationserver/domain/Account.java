package com.pigeonkim.oauth2authorizationserver.domain;

import com.pigeonkim.oauth2authorizationserver.crypto.StringEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "account",
        indexes = {
                // 관리자 전화 검색은 이 blind index 컬럼으로 동등검색한다
                @Index(name = "idx_account_phone_idx", columnList = "phone_idx")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String displayName;

    @Convert(converter = StringEncryptConverter.class)
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "phone_idx")
    private String phoneIdx;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "account")
    private List<Credential> credentials = new ArrayList<>();

    public static Account of(String displayName) {
        Account account = new Account();
        account.displayName = displayName;
        account.role = Role.ROLE_USER;
        account.status = AccountStatus.PENDING;
        return account;
    }

    public void changePhone(String phoneNumber, String phoneIdx) {
        this.phoneNumber = phoneNumber;
        this.phoneIdx = phoneIdx;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }
}
