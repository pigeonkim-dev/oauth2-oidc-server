package com.pigeonkim.oauth2authorizationserver.config;

// TODO import: OAuth2AuthorizationServerConfigurer, AuthorizationServerSettings,
//              Customizer, LoginUrlAuthenticationEntryPoint, MediaType,
//              MediaTypeRequestMatcher, SecurityFilterChain, HttpSecurity ...

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.pigeonkim.oauth2authorizationserver.repository.JpaOAuth2AuthorizationRepository;
import com.pigeonkim.oauth2authorizationserver.service.JpaOAuth2AuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
@EnableWebSecurity                 // 필터체인 커스터마이즈할 거라 명시
public class SecurityConfig {

    @Bean
    @Order(1)                      // ← SAS 프로토콜 엔드포인트 전용 체인 (먼저 매칭)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServer =
                new OAuth2AuthorizationServerConfigurer(); // 2.0 팩토리

        http
                // (1) 이 체인이 낚아챌 경로 = SAS 엔드포인트들의 matcher
                .securityMatcher(
                        authorizationServer.getEndpointsMatcher()
                )
                // (2) 설정자 장착 + OIDC 켜기
                .with(authorizationServer, (server) -> server
                        .oidc(Customizer.withDefaults())          // OpenID Connect 1.0 on
                )
                // (3) 이 체인의 모든 요청은 인증 필요
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                // (4) 브라우저(text/html) 미인증 → /login 으로 리다이렉트
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                ));

        return http.build();
    }

    @Bean
    @Order(2)                      // ← 나머지 전부: 폼 로그인
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults()) // 기본 로그인 폼
                .oauth2Login(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {   // ← 우리 BCrypt 빈 주입
        UserDetails user = User.withUsername("user")
                .password(encoder.encode("/"))   // BCrypt 인코더로 해시해서 저장
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient client = RegisteredClient.withId("11111111-1111-1111-1111-111111111111")
                .clientId("demo-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // 공개 SPA=시크릿 없음
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:3000/callback")     // 더미 SPA 콜백(임시)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)              // ③ PKCE 필수 명시
                        .requireAuthorizationConsent(true)  // ④ 구글식 동의 화면 켜기
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(client);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${app.jwk.keystore}") String keystorePath,
            @Value("${app.jwk.alias}") String alias,
            @Value("${app.jwk.password}") String password) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");           // ① 키스토어 타입
        try (InputStream in = new FileInputStream(keystorePath)) {    // ② 파일 열기
            keyStore.load(in, password.toCharArray());   // ③ 키스토어 언락
        }

        RSAKey rsaKey = RSAKey.load(keyStore, alias, password.toCharArray()); // ④ 키 꺼내기

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));             // ⑤ 이전과 동일
    }
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JpaOAuth2AuthorizationRepository repository,       // JdbcTemplate → 우리 리포지토리
            RegisteredClientRepository registeredClientRepository) {
        return new JpaOAuth2AuthorizationService(repository, registeredClientRepository);  // Jdbc → Jpa
    }


}