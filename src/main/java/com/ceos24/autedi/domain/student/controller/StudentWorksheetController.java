package com.ceos24.autedi.domain.student.controller;

import com.ceos24.autedi.domain.student.dto.request.StudentWorksheetSearchRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. Student", description = "학생별 저장 결과물 목록")
@RestController
@RequestMapping("/api/v1/students/{studentId}/worksheets")
@PreAuthorize("hasAnyRole('DIRECTOR', 'TEACHER')")
@RequiredArgsConstructor
public class StudentWorksheetController {
    private final WorksheetListService service;

    @Operation(
            summary = "학생 첨삭 결과물 목록",
            description = "임시저장은 업로더 본인만, 완료는 같은 기관 모두 조회합니다. 제목 검색과 저장일 기간 필터, 0부터 시작하는 페이지를 지원하며 최근 저장일·ID 내림차순입니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<SavedWorksheetListResponse>> getResults(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long studentId,
            @Valid @ModelAttribute StudentWorksheetSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.onSuccess(
                service.getStudentResults(principal.userId(), principal.academyId(), studentId, request)));
    }
}
