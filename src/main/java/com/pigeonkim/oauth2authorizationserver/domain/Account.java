package com.pigeonkim.oauth2authorizationserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String displayName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "account")     // ★ mappedBy 가 핵심
    private List<Credential> credentials = new ArrayList<>();

    public  static  Account of(String displayName){
        Account account = new Account();
        account.displayName = displayName;
        return account;
    }
}
