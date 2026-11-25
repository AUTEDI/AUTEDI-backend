package com.ceos24.autedi.domain.teacher.controller;

import com.ceos24.autedi.domain.teacher.dto.response.TeacherResignResponse;
import com.ceos24.autedi.domain.teacher.service.TeacherService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "09. Teacher", description = "선생님 관리(원장 전용) API")
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @Operation(
            summary = "선생님 퇴사 처리 (원장 전용)",
            description =
                    "같은 기관의 선생님을 퇴사(RESIGNED) 상태로 바꾸고 로그인을 차단하며 모든 Refresh Token을 폐기합니다. 학생 결과물과 첨삭 이력은 보존됩니다. 이미 발급된 Access Token은 만료될 때까지 유효합니다.")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping("/{teacherId}/resign")
    public ResponseEntity<ApiResponse<TeacherResignResponse>> resignTeacher(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long teacherId) {
        TeacherResignResponse response = teacherService.resign(principal.academyId(), teacherId);
        return ResponseEntity.ok(ApiResponse.onSuccess("선생님이 퇴사 처리되었습니다.", response));
    }
}
