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

@Tag(name = "05. Generation", description = "AI 첨삭 비동기 생성 요청, 상태 조회 및 실패 재시도(F04) API")
@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}/generations")
@RequiredArgsConstructor
public class WorksheetGenerationController {

    private final WorksheetGenerationService generationService;

    @Operation(
            summary = "AI 첨삭 생성 요청 접수 (F04)",
            description =
                    "저장된 원본과 설정으로 비동기 AI 분석을 시작하고 작업 ID를 반환합니다. Idempotency-Key 헤더를 통한 중복 방지(멱등성)를 지원합니다. (202 Accepted)")
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
            description = "재시도 가능한 최신 실패 작업을 다시 실행합니다. 원본 파일과 설정이 변경되지 않은 경우에만 접수됩니다. (202 Accepted)")
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
