package com.ceos24.autedi.domain.auth.controller;

import com.ceos24.autedi.domain.auth.dto.request.QrSessionCreateRequest;
import com.ceos24.autedi.domain.auth.dto.request.QrTokenRequest;
import com.ceos24.autedi.domain.auth.dto.response.LoginResponse;
import com.ceos24.autedi.domain.auth.dto.response.QrSessionCreateResponse;
import com.ceos24.autedi.domain.auth.dto.response.QrSessionStatusResponse;
import com.ceos24.autedi.domain.auth.service.QrSessionService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-1. Auth (QR 로그인)", description = "휴대폰 QR 로그인 세션 API. scan/status/token은 인증 없이 qrToken으로만 동작합니다.")
@RestController
@RequestMapping("/api/v1/auth/qr-sessions")
@RequiredArgsConstructor
public class QrSessionController {

    private final QrSessionService qrSessionService;

    @Operation(
            summary = "QR 세션 생성 (인증)",
            description = "노트북에서 호출합니다. sessionId, 만료 시각, QR 이미지(base64 data URI)를 반환합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<QrSessionCreateResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody(required = false) QrSessionCreateRequest request) {
        QrSessionCreateResponse response = qrSessionService.create(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess("QR 세션이 생성되었습니다.", response));
    }

    @Operation(summary = "QR 스캔 (비인증)", description = "휴대폰이 QR을 스캔했음을 알립니다. PENDING → SCANNED. 한 번만 성공합니다.")
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<Void>> scan(
            @Valid @RequestBody QrTokenRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                    String userAgent) {
        qrSessionService.scan(request.qrToken(), resolveDeviceInfo(userAgent));
        return ResponseEntity.ok(ApiResponse.onSuccess("QR 스캔이 처리되었습니다."));
    }

    @Operation(summary = "로그인 허용 (인증, 세션 소유자만)", description = "SCANNED → APPROVED. 세션을 만든 사용자만 호출할 수 있습니다.")
    @PostMapping("/{sessionId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String sessionId) {
        qrSessionService.approve(principal.userId(), sessionId);
        return ResponseEntity.ok(ApiResponse.onSuccess("로그인 요청을 허용했습니다."));
    }

    @Operation(summary = "로그인 거절 (인증, 세션 소유자만)", description = "PENDING/SCANNED → REJECTED. 거절된 QR은 재사용할 수 없습니다.")
    @PostMapping("/{sessionId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String sessionId) {
        qrSessionService.reject(principal.userId(), sessionId);
        return ResponseEntity.ok(ApiResponse.onSuccess("로그인 요청을 거절했습니다."));
    }

    @Operation(summary = "QR 세션 상태 조회 - 폴링 (비인증)", description = "휴대폰이 1~2초 간격으로 호출합니다. qrToken은 body로 전달합니다.")
    @PostMapping("/status")
    public ResponseEntity<ApiResponse<QrSessionStatusResponse>> status(@Valid @RequestBody QrTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.onSuccess(qrSessionService.getStatus(request.qrToken())));
    }

    @Operation(
            summary = "QR 로그인 토큰 발급 (비인증)",
            description = "APPROVED 상태에서 Access/Refresh Token을 발급하고 COMPLETED로 전환합니다. 단 한 번만 성공합니다.")
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<LoginResponse>> token(@Valid @RequestBody QrTokenRequest request) {
        LoginResponse response = qrSessionService.issueToken(request.qrToken());
        return ResponseEntity.ok(ApiResponse.onSuccess("로그인에 성공했습니다.", response));
    }

    private String resolveDeviceInfo(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        if (userAgent.contains("iPhone")) {
            return "iPhone";
        }
        if (userAgent.contains("iPad")) {
            return "iPad";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        return "Unknown";
    }
}
