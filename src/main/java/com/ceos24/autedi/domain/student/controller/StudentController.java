package com.ceos24.autedi.domain.student.controller;

import com.ceos24.autedi.domain.student.dto.request.StudentCreateRequest;
import com.ceos24.autedi.domain.student.dto.response.StudentResponse;
import com.ceos24.autedi.domain.student.service.StudentService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. Student", description = "학생 등록 및 상세 조회 API (F08·F09)")
@RestController
@RequestMapping("/api/v1/students")
@PreAuthorize("hasAnyRole('DIRECTOR', 'TEACHER')")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @Operation(
            summary = "학생 등록 (F08)",
            description = "로그인한 원장·선생님의 기관에 학생을 등록합니다. 이름·학년은 필수이며 반·학생 연락처·보호자 연락처는 선택입니다. (201 Created)")
    @PostMapping
    public ResponseEntity<ApiResponse<StudentResponse>> createStudent(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody StudentCreateRequest request) {
        StudentResponse response = studentService.createStudent(principal.academyId(), request);
        URI location = URI.create("/api/v1/students/" + response.studentId());
        return ResponseEntity.created(location).body(ApiResponse.onSuccess("학생이 등록되었습니다.", response));
    }

    @Operation(
            summary = "학생 상세 조회 (F09)",
            description = "같은 기관 학생의 이름·학년·반·연락처를 조회합니다. 다른 기관 학생과 존재하지 않는 학생은 동일한 404 응답을 반환합니다.")
    @GetMapping("/{studentId}")
    public ResponseEntity<ApiResponse<StudentResponse>> getStudent(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학생 고유 ID", required = true) @PathVariable("studentId") Long studentId) {
        StudentResponse response = studentService.getStudent(principal.academyId(), studentId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
