package com.ceos24.autedi.domain.academy.controller;

import com.ceos24.autedi.domain.academy.dto.request.AcademyUpdateRequest;
import com.ceos24.autedi.domain.academy.dto.response.AcademyResponse;
import com.ceos24.autedi.domain.academy.service.AcademyService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "08. Academy", description = "기관(학원) 정보 관리 API")
@RestController
@RequestMapping("/api/v1/academies")
@RequiredArgsConstructor
public class AcademyController {

    private final AcademyService academyService;

    @Operation(
            summary = "기관명 수정 (원장 전용)",
            description = "로그인한 원장이 소속 기관의 이름을 수정합니다. 앞뒤 공백은 제거되어 저장되며, 선생님은 호출할 수 없습니다.")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<AcademyResponse>> updateMyAcademyName(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody AcademyUpdateRequest request) {
        AcademyResponse response = academyService.updateName(principal.academyId(), request);
        return ResponseEntity.ok(ApiResponse.onSuccess("기관명이 수정되었습니다.", response));
    }
}
