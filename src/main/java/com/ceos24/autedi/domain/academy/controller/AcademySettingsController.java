package com.ceos24.autedi.domain.academy.controller;

import com.ceos24.autedi.domain.academy.dto.request.AcademySettingsRequest.*;
import com.ceos24.autedi.domain.academy.dto.response.AcademySettingsResponse;
import com.ceos24.autedi.domain.academy.dto.response.AcademySettingsResponse.*;
import com.ceos24.autedi.domain.academy.service.AcademySettingsService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "08. Academy", description = "학원별 학년·반 설정")
@RestController
@RequestMapping("/api/v1/academies/me")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DIRECTOR','TEACHER')")
public class AcademySettingsController {
    private final AcademySettingsService service;

    @GetMapping("/settings")
    @Operation(summary = "학원 설정·학생 선택지 조회", description = "학년 전체(사용 여부 포함)와 반 목록. 첫 접근에서 기본 학년과 기존 학생의 학년·반을 초기화합니다.")
    public ResponseEntity<ApiResponse<AcademySettingsResponse>> get(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.onSuccess(service.get(user.academyId())));
    }

    @PutMapping("/grades")
    @PreAuthorize("hasRole('DIRECTOR')")
    @Operation(summary = "사용 학년 전체 저장", description = "gradeIds에 포함된 학년만 사용. 빈 배열 허용. 기존 학생 배정은 유지합니다.")
    public ResponseEntity<ApiResponse<AcademySettingsResponse>> selectGrades(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user,
            @Valid @RequestBody GradeSelection request) {
        return ResponseEntity.ok(ApiResponse.onSuccess(service.selectGrades(user.academyId(), request.gradeIds())));
    }

    @PostMapping("/grades")
    @PreAuthorize("hasRole('DIRECTOR')")
    @Operation(summary = "추가 학년 생성", description = "CUSTOM 학년을 생성하고 바로 사용 상태로 설정합니다. 같은 학원 내 이름 중복 불가.")
    public ResponseEntity<ApiResponse<GradeOption>> createGrade(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user,
            @Valid @RequestBody GradeCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(service.createGrade(user.academyId(), request.name())));
    }

    @PostMapping("/classes")
    @PreAuthorize("hasRole('DIRECTOR')")
    @Operation(summary = "반 생성")
    public ResponseEntity<ApiResponse<ClassOption>> createClass(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user,
            @Valid @RequestBody ClassName request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(service.createClass(user.academyId(), request.name())));
    }

    @PatchMapping("/classes/{classId}")
    @PreAuthorize("hasRole('DIRECTOR')")
    @Operation(summary = "반 이름 수정", description = "학생의 반 ID는 유지하며 현재 학생 정보에 새 이름을 표시합니다. 과거 첨삭 저장본은 변경하지 않습니다.")
    public ResponseEntity<ApiResponse<ClassOption>> renameClass(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user,
            @PathVariable Long classId,
            @Valid @RequestBody ClassName request) {
        return ResponseEntity.ok(ApiResponse.onSuccess(service.renameClass(user.academyId(), classId, request.name())));
    }

    @DeleteMapping("/classes/{classId}")
    @PreAuthorize("hasRole('DIRECTOR')")
    @Operation(summary = "반 삭제", description = "학생이 한 명이라도 배정돼 있으면 409. 미사용 반만 삭제합니다.")
    public ResponseEntity<Void> deleteClass(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal user, @PathVariable Long classId) {
        service.deleteClass(user.academyId(), classId);
        return ResponseEntity.noContent().build();
    }
}
