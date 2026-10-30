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
import com.ceos24.autedi.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01. Auth", description = "회원가입, 로그인, 토큰 재발급(RTR), 로그아웃 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;

    @Operation(
            summary = "초대 기반 회원가입",
            description = "유효한 초대 토큰을 검증받은 선생님이 계정을 등록합니다. 가입 완료 후 보안상 자동 로그인은 지원하지 않으며 직접 로그인해야 합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = invitationService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess("회원가입이 완료되었습니다.", response));
    }

    @Operation(summary = "이메일 로그인", description = "이메일과 비밀번호로 로그인하여 Access Token(1시간)과 Refresh Token(14일)을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.onSuccess("로그인에 성공했습니다.", response));
    }

    @Operation(
            summary = "토큰 재발급 (Refresh Token Rotation)",
            description = "Access Token 만료 시 보유 중인 Refresh Token으로 새 Access/Refresh 토큰 쌍을 재발급받습니다. 기존 토큰은 즉시 무효화됩니다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.onSuccess("토큰이 갱신되었습니다.", response));
    }

    @Operation(summary = "로그아웃", description = "현재 기기의 Refresh Token을 서버 DB에서 즉시 폐기(revoked)하여 탈취된 토큰을 통한 재발급을 방지합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.onSuccess("로그아웃되었습니다."));
    }
}
