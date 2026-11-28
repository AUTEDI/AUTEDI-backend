package com.ceos24.autedi.domain.insight.controller;

import com.ceos24.autedi.domain.insight.dto.StudentInsightResponse;
import com.ceos24.autedi.domain.insight.service.StudentInsightService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "10. Student", description = "학생 AI 인사이트")
@RestController
@RequestMapping("/api/v1/students/{studentId}/insights")
@PreAuthorize("hasAnyRole('DIRECTOR','TEACHER')")
@RequiredArgsConstructor
public class StudentInsightController {
    private final StudentInsightService service;

    @GetMapping
    @Operation(summary = "저장된 AI 인사이트 조회", description = "LLM을 호출하지 않습니다. 최신 성공 결과와 갱신 필요 여부를 반환합니다.")
    public ResponseEntity<ApiResponse<StudentInsightResponse>> get(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user, @PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(service.get(user.academyId(), studentId)));
    }

    @PostMapping("/generations")
    @Operation(
            summary = "AI 인사이트 수동 생성·업데이트",
            description = "Body 없음. 동기 요청으로 생성 후 반환하며 같은 입력은 재사용합니다. 서버가 혼잡하면 503, 대기열·자동 재시도는 없습니다.")
    public ResponseEntity<ApiResponse<StudentInsightResponse>> generate(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user, @PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(service.generate(user.userId(), user.academyId(), studentId)));
    }
}
