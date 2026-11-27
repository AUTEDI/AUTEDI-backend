package com.ceos24.autedi.domain.worksheet.controller;

import com.ceos24.autedi.domain.worksheet.dto.request.RecentWorksheetSearchRequest;
import com.ceos24.autedi.domain.worksheet.dto.response.SavedWorksheetListResponse;
import com.ceos24.autedi.domain.worksheet.service.WorksheetListService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "08. Worksheet Save", description = "홈 최근 저장 작업 목록")
@RestController
@RequestMapping("/api/v1/worksheets/recent")
@PreAuthorize("hasAnyRole('DIRECTOR', 'TEACHER')")
@RequiredArgsConstructor
public class WorksheetListController {
    private final WorksheetListService service;

    @Operation(
            summary = "홈 최근 작업 목록",
            description = "최근 작업일·ID 내림차순으로 저장 작업을 조회합니다. 임시저장은 업로더 본인만, 완료는 같은 기관 모두 조회하며 미저장·삭제·저장본 없는 작업은 제외합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<SavedWorksheetListResponse>> getRecent(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @ModelAttribute RecentWorksheetSearchRequest request) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess(service.getRecent(principal.userId(), principal.academyId(), request)));
    }
}
