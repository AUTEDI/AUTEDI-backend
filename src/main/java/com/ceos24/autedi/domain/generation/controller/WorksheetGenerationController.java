package com.ceos24.autedi.domain.generation.controller;

import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import com.ceos24.autedi.domain.generation.service.WorksheetGenerationService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
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

@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}/generations")
@RequiredArgsConstructor
public class WorksheetGenerationController {

    private final WorksheetGenerationService generationService;

    @PostMapping
    public ResponseEntity<ApiResponse<GenerationResponse>> requestGeneration(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        GenerationResponse response = generationService.requestGeneration(
                principal.userId(),
                principal.academyId(),
                worksheetId,
                idempotencyKey
        );
        URI location = URI.create("/api/v1/worksheets/" + worksheetId + "/generations/" + response.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .body(ApiResponse.onSuccess("AI 첨삭 생성이 접수되었습니다.", response));
    }

    @GetMapping("/{generationId}")
    public ResponseEntity<ApiResponse<GenerationResponse>> getGenerationStatus(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("generationId") Long generationId
    ) {
        GenerationResponse response = generationService.getGenerationStatus(
                principal.academyId(),
                worksheetId,
                generationId
        );
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/{generationId}/retry")
    public ResponseEntity<ApiResponse<GenerationResponse>> retryGeneration(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("generationId") Long generationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        GenerationResponse response = generationService.retryGeneration(
                principal.userId(),
                principal.academyId(),
                worksheetId,
                generationId,
                idempotencyKey
        );
        URI location = URI.create("/api/v1/worksheets/" + worksheetId + "/generations/" + response.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .body(ApiResponse.onSuccess("AI 첨삭 생성이 재시도되었습니다.", response));
    }
}
