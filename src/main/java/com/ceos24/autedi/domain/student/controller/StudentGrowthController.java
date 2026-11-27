package com.ceos24.autedi.domain.student.controller;

import com.ceos24.autedi.domain.student.dto.request.GrowthReportRequest;
import com.ceos24.autedi.domain.student.dto.response.GrowthReportResponse;
import com.ceos24.autedi.domain.student.service.StudentGrowthService;
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

@Tag(name = "10. Student", description = "학생 성장리포트")
@RestController
@RequestMapping("/api/v1/students/{studentId}/growth-report")
@PreAuthorize("hasAnyRole('DIRECTOR', 'TEACHER')")
@RequiredArgsConstructor
public class StudentGrowthController {
    private final StudentGrowthService service;

    @GetMapping
    @Operation(
            summary = "학생 성장 그래프 조회",
            description = "같은 기관의 최종 저장본에서 교사 확정 오류 피드백을 집계합니다. 기간은 KST 오늘 기준이며, 분석하지 않은 영역과 정규화 불가 값은 0으로 대체하지 않습니다.")
    public ResponseEntity<ApiResponse<GrowthReportResponse>> getReport(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long studentId,
            @Valid @ModelAttribute GrowthReportRequest request) {
        return ResponseEntity.ok(ApiResponse.onSuccess(service.getReport(principal.academyId(), studentId, request)));
    }
}
