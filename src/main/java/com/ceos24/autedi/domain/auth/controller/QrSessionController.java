package com.ceos24.autedi.domain.auth.controller;

import com.ceos24.autedi.domain.auth.dto.request.QrSessionCreateRequest;
import com.ceos24.autedi.domain.auth.dto.request.QrTokenRequest;
import com.ceos24.autedi.domain.auth.dto.response.QrLoginResponse;
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

@Tag(
        name = "01-1. Auth (QR 로그인)",
        description = "휴대폰 QR 로그인 세션 API. scan/status/token은 인증 없이 qrToken으로만 동작하고, link는 인증이 필요합니다.")
@RestController
@RequestMapping("/api/v1/auth/qr-sessions")
@RequiredArgsConstructor
public class QrSessionController {

    private final QrSessionService qrSessionService;

    @Operation(
            summary = "QR 세션 생성 (인증)",
            description =
                    "노트북의 작업 화면에서 호출합니다. worksheetId는 필수이며, 요청자가 소유한 같은 학원의 작업이어야 합니다. sessionId, 만료 시각, QR 이미지(base64 data URI)를 반환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "QR 세션 생성 성공",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                value =
                                                        "{\"isSuccess\":true,\"code\":\"COMMON_200\",\"message\":\"QR 세션이 생성되었습니다.\",\"result\":{\"sessionId\":\"3f1c2b8e-7a41-4f0e-9c52-0d2b6a1e8f77\",\"expiresAt\":\"2026-11-11T14:35:00\",\"qrImage\":\"data:image/png;base64,iVBORw0KGgo...\"},\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "COMMON_001: worksheetId 누락",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "COMMON_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"COMMON_001\",\"message\":\"요청 값이 올바르지 않습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "AUTH_002: 작업 소유자가 아님",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_002\",\"message\":\"접근 권한이 없습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "WORKSHEET_001: 작업이 없거나 다른 학원의 작업",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "WORKSHEET_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"WORKSHEET_001\",\"message\":\"학습지를 찾을 수 없거나 접근 권한이 없습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "AUTH_001: 인증이 필요합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_001\",\"message\":\"인증이 필요합니다.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<QrSessionCreateResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody QrSessionCreateRequest request) {
        QrSessionCreateResponse response = qrSessionService.create(principal.userId(), principal.academyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess("QR 세션이 생성되었습니다.", response));
    }

    @Operation(summary = "QR 스캔 (비인증)", description = "휴대폰이 QR을 스캔했음을 알립니다. PENDING → SCANNED. 한 번만 성공합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "스캔 처리 성공. 세션 소유자 노트북에 qr.scanned 이벤트가 전송됩니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                value =
                                                        "{\"isSuccess\":true,\"code\":\"COMMON_200\",\"message\":\"성공적으로 처리되었습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "QR_001: 유효하지 않은 QR입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_001\",\"message\":\"유효하지 않은 QR입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "QR_002: 만료된 QR입니다. 새로 생성해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_002\",\"message\":\"만료된 QR입니다. 새로 생성해 주세요.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "QR_003: 이미 사용되었거나 처리할 수 없는 QR 상태입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_003",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_003\",\"message\":\"이미 사용되었거나 처리할 수 없는 QR 상태입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "COMMON_429: 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "COMMON_429",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"COMMON_429\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<Void>> scan(
            @Valid @RequestBody QrTokenRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                    String userAgent) {
        qrSessionService.scan(request.qrToken(), resolveDeviceInfo(userAgent));
        return ResponseEntity.ok(ApiResponse.onSuccess("QR 스캔이 처리되었습니다."));
    }

    @Operation(summary = "로그인 허용 (인증, 세션 소유자만)", description = "SCANNED → APPROVED. 세션을 만든 사용자만 호출할 수 있습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "처리 성공",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                value =
                                                        "{\"isSuccess\":true,\"code\":\"COMMON_200\",\"message\":\"성공적으로 처리되었습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "AUTH_001: 인증이 필요합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_001\",\"message\":\"인증이 필요합니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "AUTH_002: 세션을 만든 사용자가 아님",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_002\",\"message\":\"접근 권한이 없습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "QR_001: 유효하지 않은 QR입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_001\",\"message\":\"유효하지 않은 QR입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "QR_002: 만료된 QR입니다. 새로 생성해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_002\",\"message\":\"만료된 QR입니다. 새로 생성해 주세요.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "QR_003: 이미 사용되었거나 처리할 수 없는 QR 상태입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_003",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_003\",\"message\":\"이미 사용되었거나 처리할 수 없는 QR 상태입니다.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping("/{sessionId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String sessionId) {
        qrSessionService.approve(principal.userId(), sessionId);
        return ResponseEntity.ok(ApiResponse.onSuccess("로그인 요청을 허용했습니다."));
    }

    @Operation(summary = "로그인 거절 (인증, 세션 소유자만)", description = "PENDING/SCANNED → REJECTED. 거절된 QR은 재사용할 수 없습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "처리 성공",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                value =
                                                        "{\"isSuccess\":true,\"code\":\"COMMON_200\",\"message\":\"성공적으로 처리되었습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "AUTH_001: 인증이 필요합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_001\",\"message\":\"인증이 필요합니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "AUTH_002: 세션을 만든 사용자가 아님",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_002\",\"message\":\"접근 권한이 없습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "QR_001: 유효하지 않은 QR입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_001\",\"message\":\"유효하지 않은 QR입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "QR_002: 만료된 QR입니다. 새로 생성해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_002\",\"message\":\"만료된 QR입니다. 새로 생성해 주세요.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "QR_003: 이미 사용되었거나 처리할 수 없는 QR 상태입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_003",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_003\",\"message\":\"이미 사용되었거나 처리할 수 없는 QR 상태입니다.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping("/{sessionId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String sessionId) {
        qrSessionService.reject(principal.userId(), sessionId);
        return ResponseEntity.ok(ApiResponse.onSuccess("로그인 요청을 거절했습니다."));
    }

    @Operation(summary = "QR 세션 상태 조회 - 폴링 (비인증)", description = "휴대폰이 1~2초 간격으로 호출합니다. qrToken은 body로 전달합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "현재 상태 (PENDING/SCANNED/APPROVED/COMPLETED/REJECTED/EXPIRED)",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                value =
                                                        "{\"isSuccess\":true,\"code\":\"COMMON_200\",\"message\":\"성공적으로 처리되었습니다.\",\"result\":{\"status\":\"SCANNED\"},\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "QR_001: 유효하지 않은 QR입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_001\",\"message\":\"유효하지 않은 QR입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "COMMON_429: 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "COMMON_429",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"COMMON_429\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping("/status")
    public ResponseEntity<ApiResponse<QrSessionStatusResponse>> status(@Valid @RequestBody QrTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.onSuccess(qrSessionService.getStatus(request.qrToken())));
    }

    @Operation(
            summary = "QR 로그인 토큰 발급 (비인증)",
            description = "APPROVED 상태에서 Access/Refresh Token을 발급하고 COMPLETED로 전환합니다. 단 한 번만 성공합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description =
                        "토큰 발급 성공. 로그인 응답에 사진을 올릴 대상 작업 ID(worksheetId)가 추가되어 내려옵니다. 발급된 액세스 토큰에는 QR 로그인 표시가 들어가며, 해당 QR 세션의 업로드 대상 검증이 적용됩니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "QR_001: 유효하지 않은 QR입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_001\",\"message\":\"유효하지 않은 QR입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "QR_002: 만료된 QR입니다. 새로 생성해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_002\",\"message\":\"만료된 QR입니다. 새로 생성해 주세요.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "QR_003: APPROVED가 아니거나 이미 발급됨",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_003",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_003\",\"message\":\"이미 사용되었거나 처리할 수 없는 QR 상태입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "AUTH_007: 비활성화된 계정입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_007",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_007\",\"message\":\"비활성화된 계정입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "COMMON_429: 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "COMMON_429",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"COMMON_429\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<QrLoginResponse>> token(@Valid @RequestBody QrTokenRequest request) {
        QrLoginResponse response = qrSessionService.issueToken(request.qrToken());
        return ResponseEntity.ok(ApiResponse.onSuccess("로그인에 성공했습니다.", response));
    }

    @Operation(
            summary = "QR 연결 (인증, 같은 사용자만)",
            description = "이미 로그인된 폰이 새 QR을 찍었을 때 승인 없이 연결합니다. PENDING → COMPLETED. "
                    + "폰의 Bearer 액세스 토큰 사용자와 QR 세션 사용자가 같을 때만 성공하며, 다르면 QR_005로 거절됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description =
                        "연결 성공. /token과 같은 QrLoginResponse(로그인 응답 + 대상 작업 worksheetId)를 반환하며 새 토큰에는 새 qrSessionId가 들어갑니다. 세션 소유자 노트북에 qr.linked 이벤트가 전송됩니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                value =
                                                        "{\"isSuccess\":true,\"code\":\"COMMON_200\",\"message\":\"QR 연결에 성공했습니다.\",\"result\":{\"accessToken\":\"eyJhbGciOi...\",\"refreshToken\":\"3f1c2b8e7a414f0e9c520d2b6a1e8f77\",\"user\":{\"userId\":45,\"email\":\"teacher@autedi.com\",\"name\":\"김선생\",\"role\":\"TEACHER\",\"academyId\":1,\"academyName\":\"에이플러스 학원\",\"subscriptionStatus\":\"ACTIVE\"},\"worksheetId\":12},\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "COMMON_001: qrToken 누락",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "COMMON_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"COMMON_001\",\"message\":\"요청 값이 올바르지 않습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "AUTH_001: 인증이 필요합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_001\",\"message\":\"인증이 필요합니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "QR_005: 토큰의 사용자와 QR 세션의 사용자가 다름. 클라이언트는 scan → 데스크톱 승인 → token 흐름으로 전환합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_005",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_005\",\"message\":\"다른 계정으로 로그인된 기기입니다. 데스크톱 승인 방식으로 QR 로그인을 진행해 주세요.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "QR_001: 유효하지 않은 QR입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_001\",\"message\":\"유효하지 않은 QR입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "QR_002: 만료된 QR입니다. 새로 생성해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_002\",\"message\":\"만료된 QR입니다. 새로 생성해 주세요.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "QR_003: 이미 사용되었거나 처리할 수 없는 QR 상태입니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_003",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_003\",\"message\":\"이미 사용되었거나 처리할 수 없는 QR 상태입니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "COMMON_429: 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "COMMON_429",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"COMMON_429\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<QrLoginResponse>> link(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody QrTokenRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                    String userAgent) {
        QrLoginResponse response =
                qrSessionService.link(principal.userId(), request.qrToken(), resolveDeviceInfo(userAgent));
        return ResponseEntity.ok(ApiResponse.onSuccess("QR 연결에 성공했습니다.", response));
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
