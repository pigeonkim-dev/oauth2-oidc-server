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
        // TODO: 신규 가입 기본값 설정
        //   account.role   = Role.ROLE_USER;
        //   account.status = AccountStatus.PENDING;   // 이메일 검증 전 반쪽 계정

        account.role = Role.ROLE_USER;
        account.status = AccountStatus.PENDING;
        return account;
    }

    /**
     * 전화번호 설정/변경.
     * blind index 는 엔티티가 만들지 않는다(BlindIndexHasher 는 Spring 빈, 엔티티는 순수 도메인 유지).
     * 서비스가 hasher.hash(phone) 로 idx 를 계산해 phone 과 idx 를 '둘 다' 넘겨준다 — 여기선 저장만.
     */
    public void changePhone(String phoneNumber, String phoneIdx) {
        // TODO: 두 필드에 대입
        this.phoneNumber = phoneNumber;
        this.phoneIdx = phoneIdx;
    }
}
