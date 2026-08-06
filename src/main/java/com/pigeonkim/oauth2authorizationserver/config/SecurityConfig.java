package com.pigeonkim.oauth2authorizationserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.pigeonkim.oauth2authorizationserver.domain.Credential;
import com.pigeonkim.oauth2authorizationserver.domain.CredentialType;
import com.pigeonkim.oauth2authorizationserver.repository.AccountRepository;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;
import com.pigeonkim.oauth2authorizationserver.repository.JpaOAuth2AuthorizationRepository;
import com.pigeonkim.oauth2authorizationserver.service.JpaOAuth2AuthorizationService;
import com.pigeonkim.oauth2authorizationserver.service.RefreshTokenService;
import com.pigeonkim.oauth2authorizationserver.web.handler.KakaoLoginSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      RefreshTokenService refreshTokenService)
            throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServer =
                new OAuth2AuthorizationServerConfigurer(); // 2.0 팩토리

        http
                .securityMatcher(
                        authorizationServer.getEndpointsMatcher()
                )
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
                .authorizeHttpRequests(a ->
                        a.anyRequest().authenticated())
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                ));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, KakaoLoginSuccessHandler kakaoLoginSuccessHandler)
            throws Exception {
        http
                .authorizeHttpRequests(a ->
                        a.requestMatchers("/api/signup/**").permitAll()
                                .requestMatchers("/signup", "/signup/**").permitAll() // 가입 화면(Thymeleaf)은 비로그인 공개
                                .requestMatchers("/error").permitAll() // 서버 에러(500) 페이지가 인증에 막혀 /login 으로 둔갑하는 것 방지
                                .anyRequest().authenticated())
                .csrf(c -> c.ignoringRequestMatchers("/api/**"))
                .formLogin(form -> form.loginPage("/login").permitAll()) // 커스텀 로그인 화면(GET /login), 인증처리 POST /login 은 시큐리티가 담당
                .oauth2Login(o -> o.successHandler(kakaoLoginSuccessHandler));

        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        RegisteredClient client = RegisteredClient.withId("11111111-1111-1111-1111-111111111111")
                .clientId("demo-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:3000/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        RegisteredClient confidentialClient = RegisteredClient.withId("22222222-2222-2222-2222-222222222222")
                .clientId("board-client")
                .clientSecret(passwordEncoder.encode("board-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:3000/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
                .build();

        return new InMemoryRegisteredClientRepository(client, confidentialClient);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${app.jwk.keystore}") String keystorePath,
            @Value("${app.jwk.alias}") String alias,
            @Value("${app.jwk.password}") String password) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keystorePath)) {
            keyStore.load(in, password.toCharArray());
        }

        RSAKey rsaKey = RSAKey.load(keyStore, alias, password.toCharArray());

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JpaOAuth2AuthorizationRepository repository,
            RegisteredClientRepository registeredClientRepository) {
        return new JpaOAuth2AuthorizationService(repository, registeredClientRepository);
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(CredentialRepository credentialRepo) {
        return context -> {

            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            String email = context.getPrincipal().getName();

            credentialRepo.findWithAccountByEmailAndType(email, CredentialType.EMAIL_PASSWORD)
                    .map(Credential::getAccount)
                    .ifPresent(account -> {
                        context.getClaims().claim("name", account.getDisplayName());
                        context.getClaims().claim("account_id", account.getId().toString());
                    });
        };
    }

}