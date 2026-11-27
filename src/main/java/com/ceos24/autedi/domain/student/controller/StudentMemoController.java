package com.ceos24.autedi.domain.student.controller;

import com.ceos24.autedi.domain.student.dto.request.StudentMemoCreateRequest;
import com.ceos24.autedi.domain.student.dto.request.StudentMemoPageRequest;
import com.ceos24.autedi.domain.student.dto.response.StudentMemoResponse;
import com.ceos24.autedi.domain.student.service.StudentMemoService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.response.PageResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. Student", description = "학생 메모 목록 조회·등록 API (F09)")
@RestController
@RequestMapping("/api/v1/students/{studentId}/memos")
@PreAuthorize("hasAnyRole('DIRECTOR', 'TEACHER')")
@RequiredArgsConstructor
public class StudentMemoController {

    private final StudentMemoService studentMemoService;

    @Operation(
            summary = "학생 메모 목록 조회 (F09)",
            description =
                    "같은 기관 학생의 메모를 최신순으로 조회합니다. page는 0부터, size는 기본 20·최대 100입니다. 다른 기관 학생과 존재하지 않는 학생은 동일한 404 응답을 반환하고, 메모가 없는 학생은 빈 목록입니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StudentMemoResponse>>> getMemos(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학생 고유 ID", required = true) @PathVariable("studentId") Long studentId,
            @Valid @ModelAttribute StudentMemoPageRequest request) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess(studentMemoService.getMemos(principal.academyId(), studentId, request)));
    }

    @Operation(
            summary = "학생 메모 등록 (F09)",
            description =
                    "로그인한 사용자를 작성자로 같은 기관 학생의 메모를 등록합니다. 내용은 앞뒤 공백 제거 후 1~1,000자이며 작성 시각은 서버가 기록합니다. 성공은 201이고 등록한 메모를 반환합니다. 메모의 수정·삭제는 제공하지 않습니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<StudentMemoResponse>> createMemo(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학생 고유 ID", required = true) @PathVariable("studentId") Long studentId,
            @Valid @RequestBody StudentMemoCreateRequest request) {
        StudentMemoResponse response =
                studentMemoService.createMemo(principal.userId(), principal.academyId(), studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess("학생 메모가 등록되었습니다.", response));
    }
}
