package com.ceos24.autedi.domain.worksheet.controller;

import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetSavedResultResponse;
import com.ceos24.autedi.domain.worksheet.service.WorksheetSavedResultService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "08. Worksheet Save", description = "READY 학습지 임시·최종 저장 및 이어쓰기, 임시저장본 본인 조회·완료 저장본 기관 공유")
@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}")
@RequiredArgsConstructor
public class WorksheetSavedResultController {
    private final WorksheetSavedResultService service;

    @Operation(summary = "현재 서버 편집 상태 임시저장", description = "제목·학생이 지정된 READY 학습지의 최신 저장본 한 개를 보관합니다. If-Match는 선택입니다.")
    @PostMapping("/save-draft")
    public ResponseEntity<ApiResponse<WorksheetSavedResultResponse>> saveDraft(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long worksheetId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var response = service.saveDraft(principal.userId(), principal.academyId(), worksheetId, ifMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "현재 편집 내용 최종 저장",
            description =
                    "성공한 최신 AI 작업·제목·학생·페이지를 검증하고 완료 상태와 저장본을 함께 기록합니다. 정확한 현재 학습지 If-Match가 필수입니다. 완료 후 최신 태그로 반복 요청하면 기존 결과를 반환합니다.")
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<WorksheetSavedResultResponse>> complete(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long worksheetId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var response = service.complete(principal.userId(), principal.academyId(), worksheetId, ifMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "마지막 저장본 조회",
            description = "임시저장본은 업로더 본인만, 완료 저장본은 같은 기관 원장·선생님이 읽기 전용으로 조회합니다. 현재 작업본은 바꾸지 않으며 쓰기 권한은 확장하지 않습니다.")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'TEACHER')")
    @GetMapping("/saved-result")
    public ResponseEntity<ApiResponse<WorksheetSavedResultResponse>> savedResult(
            @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Long worksheetId) {
        var response = service.getSavedResult(principal.userId(), principal.academyId(), worksheetId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "마지막 임시저장본으로 이어쓰기",
            description = "저장 이후 편집을 되돌립니다. 현재 작업 중인 학습지의 정확한 If-Match가 필수이며 *는 허용하지 않습니다. 최신 편집용 ETag를 반환합니다.")
    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<ReviewResponse>> resume(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long worksheetId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var response = service.resume(principal.userId(), principal.academyId(), worksheetId, ifMatch);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.worksheet().etag())
                .body(ApiResponse.onSuccess(response));
    }
}
