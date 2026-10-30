package com.ceos24.autedi.domain.worksheet.controller;

import com.ceos24.autedi.domain.worksheet.dto.request.WorksheetSettingsUpdateRequest;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetDetailResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetSettingsResponse;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "04. Worksheet", description = "학습지 파일 관리(F02) 및 첨삭 영역/수준 설정(F03) API")
@RestController
@RequestMapping("/api/v1/worksheets")
@RequiredArgsConstructor
public class WorksheetController {

    private final WorksheetService worksheetService;

    @Operation(
            summary = "학습지 원본 파일 업로드 (F02)",
            description = "학습지 이미지(JPG, PNG) 또는 PDF 파일을 업로드하여 DRAFT 상태로 등록합니다. 응답 헤더로 최신 ETag가 발급됩니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> uploadWorksheet(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "업로드할 학습지 이미지/PDF 파일 (최대 20MB)", required = true) @RequestParam("file")
                    MultipartFile file) {
        WorksheetDetailResponse response =
                worksheetService.uploadWorksheet(principal.userId(), principal.academyId(), file);
        URI location = URI.create("/api/v1/worksheets/" + response.id());
        return ResponseEntity.created(location)
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("학습지가 등록되었습니다.", response));
    }

    @Operation(
            summary = "학습지 상세 메타데이터 및 미리보기 조회 (F02)",
            description = "학습지 기본 정보와 페이지별 미리보기 URL을 조회합니다. 타 학원 학습지 접근 시 404 NOT_FOUND로 자원을 은닉합니다.")
    @GetMapping("/{worksheetId}")
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> getWorksheet(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId) {
        WorksheetDetailResponse response = worksheetService.getWorksheetDetail(principal.academyId(), worksheetId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "학습지 원본 파일 교체 (F02)",
            description = "DRAFT 또는 FAILED 상태의 학습지에 한하여 원본 파일을 교체합니다. 교체 시 기존 OCR 캐시는 무효화되며 설정(Settings)은 보존됩니다.")
    @PutMapping(value = "/{worksheetId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> replaceWorksheetFile(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "동시성 제어를 위한 최신 ETag 값 (If-Match: \\\"ws_1-v1\\\")", required = false)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch,
            @Parameter(description = "교체할 새 학습지 파일", required = true) @RequestParam("file") MultipartFile file) {
        WorksheetDetailResponse response =
                worksheetService.replaceWorksheetFile(principal.academyId(), worksheetId, ifMatch, file);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("학습지 파일이 교체되었습니다.", response));
    }

    @Operation(
            summary = "학습지 첨삭 영역 및 개입 수준 설정 조회 (F03)",
            description = "학습지에 설정된 첨삭 영역 목록과 영역별 AI 개입 수준(STANDARD / ADVANCED)을 조회합니다.")
    @GetMapping("/{worksheetId}/settings")
    public ResponseEntity<ApiResponse<WorksheetSettingsResponse>> getSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId) {
        WorksheetSettingsResponse response = worksheetService.getSettings(principal.academyId(), worksheetId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "학습지 첨삭 영역 및 개입 수준 설정 저장/전체 교체 (F03)",
            description = "선택한 첨삭 영역과 수준을 저장합니다. 최소 1개 영역이 필수이며 기존 설정을 전체 교체합니다. AI 진행 중/완료 상태에서는 409 CONFLICT로 차단됩니다.")
    @PutMapping("/{worksheetId}/settings")
    public ResponseEntity<ApiResponse<WorksheetSettingsResponse>> updateSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "동시성 제어를 위한 최신 ETag 값 (If-Match: \\\"ws_1-v1\\\")", required = false)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch,
            @Valid @RequestBody WorksheetSettingsUpdateRequest request) {
        WorksheetSettingsResponse response =
                worksheetService.updateSettings(principal.academyId(), worksheetId, ifMatch, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("첨삭 설정이 저장되었습니다.", response));
    }
}
