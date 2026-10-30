package com.ceos24.autedi.domain.invitation.controller;

import com.ceos24.autedi.domain.invitation.dto.request.InvitationCreateRequest;
import com.ceos24.autedi.domain.invitation.dto.response.InvitationResponse;
import com.ceos24.autedi.domain.invitation.dto.response.InvitationValidateResponse;
import com.ceos24.autedi.domain.invitation.service.InvitationService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "02. Invitation", description = "선생님 이메일 초대 발송 및 토큰 검증 API")
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(
            summary = "선생님 이메일 초대장 발송 (원장 전용)",
            description =
                    "원장(DIRECTOR)이 선생님 이메일 1건으로 24시간 유효한 초대 링크를 발송합니다. 동일 이메일로 발송된 기존 대기 초대는 자동으로 무효화(REVOKED)됩니다.")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping
    public ResponseEntity<ApiResponse<InvitationResponse>> createInvitation(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody InvitationCreateRequest request) {
        InvitationResponse response = invitationService.createInvitation(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess("초대장이 발송되었습니다.", response));
    }

    @Operation(
            summary = "초대 토큰 검증 및 가입 정보 조회",
            description = "이메일 링크에 포함된 초대 토큰이 유효한지 검증하고, 회원가입 폼에 바인딩할 이메일 및 학원명을 반환합니다.")
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<InvitationValidateResponse>> validateInvitation(
            @Parameter(description = "이메일 링크에 포함된 원본 초대 토큰 문자열", required = true) @RequestParam("token") String token) {
        InvitationValidateResponse response = invitationService.validateInvitation(token);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
