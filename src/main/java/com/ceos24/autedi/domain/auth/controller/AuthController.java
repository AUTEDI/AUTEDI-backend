package com.ceos24.autedi.domain.auth.controller;

import com.ceos24.autedi.domain.auth.dto.request.LoginRequest;
import com.ceos24.autedi.domain.auth.dto.request.LogoutRequest;
import com.ceos24.autedi.domain.auth.dto.request.SignupRequest;
import com.ceos24.autedi.domain.auth.dto.request.TokenRefreshRequest;
import com.ceos24.autedi.domain.auth.dto.response.LoginResponse;
import com.ceos24.autedi.domain.auth.dto.response.SignupResponse;
import com.ceos24.autedi.domain.auth.dto.response.TokenRefreshResponse;
import com.ceos24.autedi.domain.auth.service.AuthService;
import com.ceos24.autedi.domain.invitation.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = invitationService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok().build();
    }
}
