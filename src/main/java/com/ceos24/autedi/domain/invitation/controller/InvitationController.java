package com.ceos24.autedi.domain.invitation.controller;

import com.ceos24.autedi.domain.invitation.dto.request.InvitationCreateRequest;
import com.ceos24.autedi.domain.invitation.dto.response.InvitationResponse;
import com.ceos24.autedi.domain.invitation.dto.response.InvitationValidateResponse;
import com.ceos24.autedi.domain.invitation.service.InvitationService;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping
    public ResponseEntity<InvitationResponse> createInvitation(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody InvitationCreateRequest request
    ) {
        InvitationResponse response = invitationService.createInvitation(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<InvitationValidateResponse> validateInvitation(
            @RequestParam("token") String token
    ) {
        InvitationValidateResponse response = invitationService.validateInvitation(token);
        return ResponseEntity.ok(response);
    }
}
