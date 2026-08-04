package com.pigeonkim.oauth2authorizationserver.web.handler;

import com.pigeonkim.oauth2authorizationserver.domain.Credential;
import com.pigeonkim.oauth2authorizationserver.repository.CredentialRepository;

import com.pigeonkim.oauth2authorizationserver.service.PendingKakaoCredential;
import com.pigeonkim.oauth2authorizationserver.web.controller.LinkController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * 카카오 OAuth 로그인 성공 직후 실행 (배선의 핵심).
 * 하는 일 = "이 카카오가 누구지?" 분기:
 * - 이미 연결된 카카오면 → 그 계정으로 로그인 완료 (홈으로)
 * - 신규 카카오면 → PendingKakaoCredential 을 '세션'에 저장(LinkController.confirm 이 읽는 자리) → 링크/신규 선택으로
 * ⚠️ 이메일로 자동 연결 금지(R1). 세션 저장까지만 하고, 연결은 사용자가 명시적으로.
 */
@Component
@RequiredArgsConstructor
public class KakaoLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final CredentialRepository credentialRepo;

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException {
        // TODO:
        //  1) 카카오 신원 꺼내기:
        //       OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        //       String provider    = token.getAuthorizedClientRegistrationId();  // "kakao"
        //       String providerUid = token.getPrincipal().getName();             // 카카오 id (user-name-attribute:id)
        //
        //  2) 이미 연결된 카카오인가? credentialRepo.findByProviderAndProviderUid(provider, providerUid)
        //       존재하면 → response.sendRedirect("/");  return;    // 이미 연결됨 → 홈
        //
        //  3) 신규 → 증명된 수단을 세션에 보관 (email 은 scope 상 없음 → null):
        //       PendingKakaoCredential proven = new PendingKakaoCredential(provider, providerUid, null);
        //       request.getSession().setAttribute(LinkController.PENDING_KAKAO_ATTR, proven);
        //
        //  4) response.sendRedirect("/link-choice");   // 링크할지/새계정 만들지 선택 (임시 목적지)

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        String providerUid = token.getPrincipal().getName();

        Optional<Credential> credential = credentialRepo.findByProviderAndProviderUid(provider, providerUid);

        if (credential.isPresent()) {

            response.sendRedirect("/");
            return;

        }

        PendingKakaoCredential proven = new PendingKakaoCredential(provider, providerUid, null);
        request.getSession().setAttribute(LinkController.PENDING_KAKAO_ATTR, proven);
        response.sendRedirect("/link-choice.html");
    }
}
