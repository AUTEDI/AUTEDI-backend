package com.ceos24.autedi.domain.correction.controller;

import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionListResponse;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.correction.service.CorrectionService;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "06. Review & Correction", description = "검토 화면 통합 조회 및 AI 첨삭 편집/삭제/복원(F05) API")
@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}")
@RequiredArgsConstructor
public class CorrectionController {

    private final CorrectionService correctionService;

    @Operation(
            summary = "검토 화면 통합 조회 (F05)",
            description =
                    "학습지, 페이지, 활성 첨삭, 교사 코멘트와 최신 생성의 reviewIssues, 영역별 요약, 결과 출처를 조회합니다. 첨삭에는 action과 검증 경고가 포함됩니다.")
    @GetMapping("/review")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReview(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId) {
        ReviewResponse response = correctionService.getReview(principal.academyId(), worksheetId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "AI 첨삭 목록 필터 조회 (F05)",
            description = "학습지 첨삭 목록을 조회합니다. 상태(ACTIVE / DELETED), 페이지(pageId), 영역(area)별로 선택적 필터링이 가능합니다.")
    @GetMapping("/corrections")
    public ResponseEntity<ApiResponse<CorrectionListResponse>> getCorrections(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "상태 필터 (ACTIVE / DELETED)", required = false)
                    @RequestParam(value = "status", required = false)
                    String status,
            @Parameter(description = "페이지 ID 필터", required = false) @RequestParam(value = "pageId", required = false)
                    Long pageId,
            @Parameter(description = "첨삭 영역 필터 (GRAMMAR_SPELLING 등)", required = false)
                    @RequestParam(value = "area", required = false)
                    CorrectionArea area) {
        CorrectionListResponse response =
                correctionService.getCorrections(principal.academyId(), worksheetId, status, pageId, area);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "AI 첨삭 단건 상세 조회 (F05)",
            description = "첨삭 한 건의 action(REPLACE/GUIDANCE), 원문, AI 최초 제안, 현재 수정문, anchor, 좌표, 검증 경고 및 수정 여부를 조회합니다.")
    @GetMapping("/corrections/{correctionId}")
    public ResponseEntity<ApiResponse<CorrectionResponse>> getCorrection(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "첨삭 항목 고유 ID", required = true) @PathVariable("correctionId") Long correctionId) {
        CorrectionResponse response = correctionService.getCorrection(principal.academyId(), worksheetId, correctionId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "AI 첨삭 내용(content) 수정 (F05)",
            description =
                    "선생님이 현재 내용만 편집합니다. REPLACE는 빈 문자열(삭제 제안)을 허용하고 공백을 보존하며, GUIDANCE는 빈 내용을 거부합니다. 최초 제안/원문/좌표/설명/검증 결과는 보존됩니다. ETag 동시성 제어를 지원합니다.")
    @PatchMapping("/corrections/{correctionId}")
    public ResponseEntity<ApiResponse<CorrectionResponse>> updateCorrection(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "첨삭 항목 고유 ID", required = true) @PathVariable("correctionId") Long correctionId,
            @Parameter(description = "동시성 제어를 위한 최신 ETag (If-Match: \\\"cor_1-v1\\\")", required = false)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch,
            @Valid @RequestBody CorrectionUpdateRequest request) {
        CorrectionResponse response =
                correctionService.updateCorrection(principal.academyId(), worksheetId, correctionId, ifMatch, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("첨삭 내용이 수정되었습니다.", response));
    }

    @Operation(
            summary = "AI 첨삭 소프트 삭제 (F05)",
            description = "첨삭 카드를 복원 가능한 삭제 상태(DELETED)로 변경합니다. 원본 이미지와 이전 편집 내용은 온전히 보존됩니다. (204 No Content)")
    @DeleteMapping("/corrections/{correctionId}")
    public ResponseEntity<Void> deleteCorrection(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "첨삭 항목 고유 ID", required = true) @PathVariable("correctionId") Long correctionId,
            @Parameter(description = "동시성 제어를 위한 최신 ETag", required = false)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch) {
        String nextEtag = correctionService.deleteCorrection(principal.academyId(), worksheetId, correctionId, ifMatch);
        return ResponseEntity.noContent().header(HttpHeaders.ETAG, nextEtag).build();
    }

    @Operation(
            summary = "AI 첨삭 삭제 취소/복원 (F05)",
            description = "삭제 처리된 첨삭 항목을 다시 활성화(ACTIVE)합니다. 삭제 직전의 선생님 편집 내용이 그대로 복원됩니다.")
    @PostMapping("/corrections/{correctionId}/restore")
    public ResponseEntity<ApiResponse<CorrectionResponse>> restoreCorrection(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "첨삭 항목 고유 ID", required = true) @PathVariable("correctionId") Long correctionId,
            @Parameter(description = "동시성 제어를 위한 최신 ETag", required = false)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch) {
        CorrectionResponse response =
                correctionService.restoreCorrection(principal.academyId(), worksheetId, correctionId, ifMatch);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("첨삭 삭제가 취소되었습니다.", response));
    }
}
