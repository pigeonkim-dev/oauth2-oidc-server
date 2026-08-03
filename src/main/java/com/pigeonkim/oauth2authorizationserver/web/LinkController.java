package com.pigeonkim.oauth2authorizationserver.web;

import com.pigeonkim.oauth2authorizationserver.dto.LinkInitiateRequest;
import com.pigeonkim.oauth2authorizationserver.service.LinkingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/link")
@RequiredArgsConstructor
public class LinkController {

    private final LinkingService linkingService;

    @PostMapping("/initiate")
    public ResponseEntity<Void> initiate(@Valid @RequestBody LinkInitiateRequest request) {

        linkingService.initiateLink(request.existingEmail());

        return  ResponseEntity.accepted().build();
    }
}
