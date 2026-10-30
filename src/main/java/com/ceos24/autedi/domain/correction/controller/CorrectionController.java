package com.ceos24.autedi.domain.correction.controller;

import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionListResponse;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.correction.service.CorrectionService;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
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

@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}")
@RequiredArgsConstructor
public class CorrectionController {

    private final CorrectionService correctionService;

    @GetMapping("/review")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReview(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId
    ) {
        ReviewResponse response = correctionService.getReview(principal.academyId(), worksheetId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/corrections")
    public ResponseEntity<ApiResponse<CorrectionListResponse>> getCorrections(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageId", required = false) Long pageId,
            @RequestParam(value = "area", required = false) CorrectionArea area
    ) {
        CorrectionListResponse response = correctionService.getCorrections(
                principal.academyId(),
                worksheetId,
                status,
                pageId,
                area
        );
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/corrections/{correctionId}")
    public ResponseEntity<ApiResponse<CorrectionResponse>> getCorrection(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("correctionId") Long correctionId
    ) {
        CorrectionResponse response = correctionService.getCorrection(
                principal.academyId(),
                worksheetId,
                correctionId
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess(response));
    }

    @PatchMapping("/corrections/{correctionId}")
    public ResponseEntity<ApiResponse<CorrectionResponse>> updateCorrection(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("correctionId") Long correctionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody CorrectionUpdateRequest request
    ) {
        CorrectionResponse response = correctionService.updateCorrection(
                principal.academyId(),
                worksheetId,
                correctionId,
                ifMatch,
                request
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("첨삭 내용이 수정되었습니다.", response));
    }

    @DeleteMapping("/corrections/{correctionId}")
    public ResponseEntity<Void> deleteCorrection(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("correctionId") Long correctionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        String nextEtag = correctionService.deleteCorrection(
                principal.academyId(),
                worksheetId,
                correctionId,
                ifMatch
        );
        return ResponseEntity.noContent()
                .header(HttpHeaders.ETAG, nextEtag)
                .build();
    }

    @PostMapping("/corrections/{correctionId}/restore")
    public ResponseEntity<ApiResponse<CorrectionResponse>> restoreCorrection(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("correctionId") Long correctionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        CorrectionResponse response = correctionService.restoreCorrection(
                principal.academyId(),
                worksheetId,
                correctionId,
                ifMatch
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("첨삭 삭제가 취소되었습니다.", response));
    }
}
