package com.ceos24.autedi.domain.user.controller;

import com.ceos24.autedi.domain.user.dto.request.PasswordChangeRequest;
import com.ceos24.autedi.domain.user.dto.request.UserUpdateRequest;
import com.ceos24.autedi.domain.user.dto.response.UserResponse;
import com.ceos24.autedi.domain.user.service.UserService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03. User", description = "회원 프로필 및 세션 정보 조회 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 및 소속 학원 정보 조회",
            description = "현재 로그인한 사용자의 정보, 역할(DIRECTOR / TEACHER), 소속 학원명 및 학원 구독 상태(ACTIVE, EXPIRED 등)를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal) {
        UserResponse response = userService.getCurrentUser(principal.userId());
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "내 이름 수정",
            description = "현재 로그인한 사용자의 이름을 수정합니다. 앞뒤 공백은 제거되어 저장되며, 수정된 최신 사용자 정보를 반환합니다.")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyName(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateName(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.onSuccess("이름이 수정되었습니다.", response));
    }

    @Operation(
            summary = "내 비밀번호 변경",
            description =
                    "현재 비밀번호를 확인한 뒤 새 비밀번호(8~20자, 영문·숫자·특수문자 포함)로 변경합니다. 변경 후 모든 기기의 Refresh Token이 폐기되어 재로그인이 필요하며, 이미 발급된 Access Token은 만료될 때까지 유효합니다.")
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.onSuccess("비밀번호가 변경되었습니다."));
    }
}
