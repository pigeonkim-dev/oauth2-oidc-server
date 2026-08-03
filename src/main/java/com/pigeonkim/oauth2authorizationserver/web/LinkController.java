package com.pigeonkim.oauth2authorizationserver.web;

import com.pigeonkim.oauth2authorizationserver.dto.LinkConfirmRequest;
import com.pigeonkim.oauth2authorizationserver.dto.LinkInitiateRequest;
import com.pigeonkim.oauth2authorizationserver.exception.LinkRefusedException;
import com.pigeonkim.oauth2authorizationserver.service.LinkingService;
import com.pigeonkim.oauth2authorizationserver.service.PendingKakaoCredential;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/link")
@RequiredArgsConstructor
public class LinkController {

    private final LinkingService linkingService;
    public static final String PENDING_KAKAO_ATTR = "PENDING_KAKAO_CREDENTIAL";

    @PostMapping("/initiate")
    public ResponseEntity<Void> initiate(@Valid @RequestBody LinkInitiateRequest request) {

        linkingService.initiateLink(request.existingEmail());

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody LinkConfirmRequest request,
                                        HttpSession session) {

        PendingKakaoCredential proven =
                (PendingKakaoCredential) session.getAttribute(PENDING_KAKAO_ATTR);

        if (proven == null) {
            throw new LinkRefusedException("no proven credential in session");
        }

        linkingService.confirmLink(request.existingEmail(), request.linkCode(), proven);

        session.removeAttribute(PENDING_KAKAO_ATTR);

        return ResponseEntity.ok().build();
    }
}
