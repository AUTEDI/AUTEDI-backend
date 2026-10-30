package com.ceos24.autedi.domain.worksheet.controller;

import com.ceos24.autedi.domain.worksheet.dto.request.WorksheetSettingsUpdateRequest;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetDetailResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetSettingsResponse;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
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

@RestController
@RequestMapping("/api/v1/worksheets")
@RequiredArgsConstructor
public class WorksheetController {

    private final WorksheetService worksheetService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> uploadWorksheet(
            @AuthenticationPrincipal CustomUserPrincipal principal, @RequestParam("file") MultipartFile file) {
        WorksheetDetailResponse response =
                worksheetService.uploadWorksheet(principal.userId(), principal.academyId(), file);
        URI location = URI.create("/api/v1/worksheets/" + response.id());
        return ResponseEntity.created(location)
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("학습지가 등록되었습니다.", response));
    }

    @GetMapping("/{worksheetId}")
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> getWorksheet(
            @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable("worksheetId") Long worksheetId) {
        WorksheetDetailResponse response = worksheetService.getWorksheetDetail(principal.academyId(), worksheetId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @PutMapping(value = "/{worksheetId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> replaceWorksheetFile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestParam("file") MultipartFile file) {
        WorksheetDetailResponse response =
                worksheetService.replaceWorksheetFile(principal.academyId(), worksheetId, ifMatch, file);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("학습지 파일이 교체되었습니다.", response));
    }

    @GetMapping("/{worksheetId}/settings")
    public ResponseEntity<ApiResponse<WorksheetSettingsResponse>> getSettings(
            @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable("worksheetId") Long worksheetId) {
        WorksheetSettingsResponse response = worksheetService.getSettings(principal.academyId(), worksheetId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @PutMapping("/{worksheetId}/settings")
    public ResponseEntity<ApiResponse<WorksheetSettingsResponse>> updateSettings(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody WorksheetSettingsUpdateRequest request) {
        WorksheetSettingsResponse response =
                worksheetService.updateSettings(principal.academyId(), worksheetId, ifMatch, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("첨삭 설정이 저장되었습니다.", response));
    }
}
