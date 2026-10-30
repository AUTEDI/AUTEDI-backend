package com.ceos24.autedi.domain.user.controller;

import com.ceos24.autedi.domain.auth.service.AuthService;
import com.ceos24.autedi.domain.user.dto.response.UserResponse;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal CustomUserPrincipal principal) {
        UserResponse response = authService.getCurrentUser(principal.userId());
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
