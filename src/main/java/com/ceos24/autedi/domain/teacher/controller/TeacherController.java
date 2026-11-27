package com.ceos24.autedi.domain.teacher.controller;

import com.ceos24.autedi.domain.teacher.dto.TeacherListStatus;
import com.ceos24.autedi.domain.teacher.dto.request.TeacherUpdateRequest;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherDetailResponse;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherListItemResponse;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherResignResponse;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherWorksheetHistoryItemResponse;
import com.ceos24.autedi.domain.teacher.service.TeacherService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.response.PageResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "09. Teacher", description = "선생님 관리(원장 전용) API")
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @Operation(
            summary = "선생님 목록 조회 (원장 전용)",
            description =
                    "같은 기관의 가입한 선생님과 초대 대기를 한 목록으로 이름순 조회합니다. 원장은 포함하지 않습니다. keyword는 이름·이메일 부분 일치(대소문자 무시), status는 ACTIVE(활동 중)·INVITED(초대 대기)·RESIGNED(퇴사)이며 생략하면 전체입니다. page는 0부터 시작하고 size는 기본 20, 1~100입니다. 초대 대기 행은 teacherId가 null이고 invitationId·expiresAt을 가지며, 만료 시각이 지난 초대도 포함됩니다.")
    @PreAuthorize("hasRole('DIRECTOR')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TeacherListItemResponse>>> getTeachers(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TeacherListStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<TeacherListItemResponse> response =
                teacherService.list(principal.academyId(), keyword, status, page, size);
        return ResponseEntity.ok(ApiResponse.onSuccess("선생님 목록을 조회했습니다.", response));
    }

    @Operation(
            summary = "선생님 상세 조회 (원장 전용)",
            description = "같은 기관 선생님의 이름·이메일·연락처·입사일·상태를 조회합니다. 퇴사한 선생님도 조회할 수 있습니다. 연락처·입사일은 등록 전이면 null입니다.")
    @PreAuthorize("hasRole('DIRECTOR')")
    @GetMapping("/{teacherId}")
    public ResponseEntity<ApiResponse<TeacherDetailResponse>> getTeacher(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long teacherId) {
        TeacherDetailResponse response = teacherService.getDetail(principal.academyId(), teacherId);
        return ResponseEntity.ok(ApiResponse.onSuccess("선생님 정보를 조회했습니다.", response));
    }

    @Operation(
            summary = "선생님 첨삭 이력 조회 (원장 전용)",
            description =
                    "같은 기관 선생님이 올린 임시저장·완료 학습지를 처음 저장한 시각 최신순으로 조회합니다. 삭제된 학습지는 제외합니다. keyword는 학습지 제목·학생 이름 부분 일치(대소문자 무시), month는 처음 저장한 달을 yyyy-MM으로 지정하며(한국 시간) 생략하면 전체 기간입니다. page는 0부터 시작하고 size는 기본 20, 1~100입니다. 선생님 목록의 월별 첨삭 수와 같은 기준입니다. 퇴사한 선생님도 조회할 수 있습니다.")
    @PreAuthorize("hasRole('DIRECTOR')")
    @GetMapping("/{teacherId}/worksheets")
    public ResponseEntity<ApiResponse<PageResponse<TeacherWorksheetHistoryItemResponse>>> getTeacherWorksheets(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long teacherId,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "처음 저장한 달 (yyyy-MM)", example = "2026-11")
                    @RequestParam(required = false)
                    @DateTimeFormat(pattern = "yyyy-MM")
                    YearMonth month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<TeacherWorksheetHistoryItemResponse> response =
                teacherService.getWorksheetHistory(principal.academyId(), teacherId, keyword, month, page, size);
        return ResponseEntity.ok(ApiResponse.onSuccess("선생님 첨삭 이력을 조회했습니다.", response));
    }

    @Operation(
            summary = "선생님 정보 수정 (원장 전용)",
            description =
                    "같은 기관 선생님의 이름·연락처·입사일을 수정합니다. 보내지 않았거나 null인 항목은 바꾸지 않고, 연락처는 빈 문자열이면 삭제합니다. 하나 이상은 값이 있어야 합니다. 퇴사한 선생님도 수정할 수 있으며 이메일은 바꿀 수 없습니다.")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PatchMapping("/{teacherId}")
    public ResponseEntity<ApiResponse<TeacherDetailResponse>> updateTeacher(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long teacherId,
            @Valid @RequestBody TeacherUpdateRequest request) {
        TeacherDetailResponse response = teacherService.update(principal.academyId(), teacherId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess("선생님 정보가 수정되었습니다.", response));
    }

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
