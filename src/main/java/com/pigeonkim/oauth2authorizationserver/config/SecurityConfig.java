package com.pigeonkim.oauth2authorizationserver.config;

// TODO import: OAuth2AuthorizationServerConfigurer, AuthorizationServerSettings,
//              Customizer, LoginUrlAuthenticationEntryPoint, MediaType,
//              MediaTypeRequestMatcher, SecurityFilterChain, HttpSecurity ...

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.pigeonkim.oauth2authorizationserver.repository.AccountRepository;
import com.pigeonkim.oauth2authorizationserver.repository.JpaOAuth2AuthorizationRepository;
import com.pigeonkim.oauth2authorizationserver.service.JpaOAuth2AuthorizationService;
import com.pigeonkim.oauth2authorizationserver.service.RefreshTokenService;
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
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
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
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      RefreshTokenService refreshTokenService)
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
                        .oidc(Customizer.withDefaults())
                        .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                .authenticationProviders(providers -> {
                                            for (int i = 0; i < providers.size(); i++) {
                                                if (providers.get(i)
                                                        instanceof OAuth2RefreshTokenAuthenticationProvider) {
                                                    providers.set(i,
                                                            new ReuseDetectingRefreshTokenAuthenticationProvider(
                                                                    providers.get(i), refreshTokenService));
                                                }
                                            }
                                        }
                                ))
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
                .tokenSettings(TokenSettings.builder()
                        .reuseRefreshTokens(false)      // ★ 이게 회전 스위치 — 켜야 새 refresh 발급 → 우리 rotate 호출됨
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

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(AccountRepository accountRepository) {
        return context -> {
            // (1) 액세스 토큰일 때만 손댄다 (id_token/refresh 는 제외)
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            // (2) principal → accountId 매핑
            //   TODO(M4): 지금 로그인 principal 은 데모 인메모리 user 라 우리 Account 로 못 잇는다.
            //             로그인을 Account 로 back 한 뒤(M4) 여기서 accountId 를 얻는다.
            Long accountId = null;   // ← M4 전엔 null (아래 (3)에서 안전하게 스킵)
            if (accountId == null) {
                return;              // 못 얻으면 클레임 없이 통과 — 토큰 발급 자체는 정상
            }

            // (3) Account + credentials 를 한 쿼리로 로드해서 클레임 싣기
            // TODO: accountRepository.findWithCredentialsById(accountId).ifPresent(account -> {
            //           context.getClaims().claim("name", account.getDisplayName());
            //           context.getClaims().claim("account_id", account.getId());
            //           // (옵션) providers: account.getCredentials() 의 type 들
            //       });

            accountRepository.findWithCredentialsById(accountId).ifPresent(account -> {
                context.getClaims().claim("name", account.getDisplayName());
                context.getClaims().claim("account_id", account.getId());
            });
        };
    }

}