package com.ceos24.autedi.domain.generation.controller;

import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import com.ceos24.autedi.domain.generation.service.WorksheetGenerationService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "05. Generation", description = "첨삭 백그라운드 작업 접수·상태 조회·재시도. 설정으로 Mock 또는 Terra HTTP를 선택합니다.")
@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}/generations")
@RequiredArgsConstructor
public class WorksheetGenerationController {

    private final WorksheetGenerationService generationService;

    @Operation(
            summary = "AI 첨삭 생성 요청 접수 (F04)",
            description =
                    "PENDING 작업을 저장하고 202를 반환합니다. 커밋 후 워커가 같은 보정 PNG로 OCR·AI_CORRECTION을 실행하고 단계별로 저장합니다. Idempotency-Key를 지원하며 TERRA 설정에서만 외부 요청을 전송합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<GenerationResponse>> requestGeneration(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "중복 생성 방지를 위한 클라이언트 고유 멱등키 (UUID 등)", required = false)
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey) {
        GenerationResponse response = generationService.requestGeneration(
                principal.userId(), principal.academyId(), worksheetId, idempotencyKey);
        URI location = URI.create("/api/v1/worksheets/" + worksheetId + "/generations/" + response.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .body(ApiResponse.onSuccess("AI 첨삭 생성이 접수되었습니다.", response));
    }

    @Operation(
            summary = "AI 첨삭 생성 상태 조회 (F04)",
            description = "작업의 대기/실행/성공/실패 상태(PENDING, RUNNING, SUCCEEDED, FAILED), 처리 단계 및 재시도 가능 여부를 폴링 조회합니다.")
    @GetMapping("/{generationId}")
    public ResponseEntity<ApiResponse<GenerationResponse>> getGenerationStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "생성 작업 고유 ID", required = true) @PathVariable("generationId") Long generationId) {
        GenerationResponse response =
                generationService.getGenerationStatus(principal.academyId(), worksheetId, generationId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "실패한 AI 첨삭 작업 재시도 (F04)",
            description =
                    "재시도 가능한 최신 실패 작업만 접수합니다. 파일·설정이 같고 유효한 전사가 있으면 첨삭부터 실행합니다. 같은 멱등키는 같은 작업을 반환하며, 생성 요청 또는 다른 재시도의 키를 재사용하면 409입니다. (202 Accepted)")
    @PostMapping("/{generationId}/retry")
    public ResponseEntity<ApiResponse<GenerationResponse>> retryGeneration(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "생성 작업 고유 ID", required = true) @PathVariable("generationId") Long generationId,
            @Parameter(description = "중복 생성 방지를 위한 클라이언트 고유 멱등키 (UUID 등)", required = false)
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey) {
        GenerationResponse response = generationService.retryGeneration(
                principal.userId(), principal.academyId(), worksheetId, generationId, idempotencyKey);
        URI location = URI.create("/api/v1/worksheets/" + worksheetId + "/generations/" + response.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .body(ApiResponse.onSuccess("AI 첨삭 생성이 재시도되었습니다.", response));
    }
}
