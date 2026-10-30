package com.ceos24.autedi.domain.user.controller;

import com.ceos24.autedi.domain.auth.service.AuthService;
import com.ceos24.autedi.domain.user.dto.response.UserResponse;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03. User", description = "회원 프로필 및 세션 정보 조회 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @Operation(
            summary = "내 정보 및 소속 학원 정보 조회",
            description = "현재 로그인한 사용자의 정보, 역할(DIRECTOR / TEACHER), 소속 학원명 및 학원 구독 상태(ACTIVE, EXPIRED 등)를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal) {
        UserResponse response = authService.getCurrentUser(principal.userId());
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
