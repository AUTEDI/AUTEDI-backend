package com.ceos24.autedi.domain.worksheet.controller;

import com.ceos24.autedi.domain.worksheet.dto.request.WorksheetSettingsUpdateRequest;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetDetailResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetSettingsResponse;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import com.ceos24.autedi.domain.worksheet.service.WorksheetFileResource;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Tag(name = "04. Worksheet", description = "학습지 파일 관리(F02) 및 첨삭 영역/수준 설정(F03) API")
@RestController
@RequestMapping("/api/v1/worksheets")
@RequiredArgsConstructor
public class WorksheetController {

    private final WorksheetService worksheetService;

    @Operation(
            summary = "학습지 원본 파일 업로드 (F02)",
            description = "JPG/JPEG·PNG 한 장의 원본을 보존하고 EXIF 보정 PNG를 준비해 DRAFT 상태로 등록합니다. PDF는 이번 연동 범위에서 지원하지 않습니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> uploadWorksheet(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "업로드할 JPG/JPEG·PNG 한 장 (원본·보정 PNG 각각 최대 20MB)", required = true)
                    @RequestParam("file")
                    MultipartFile file,
            @Parameter(hidden = true) MultipartHttpServletRequest multipartRequest) {
        checkSingleImage(multipartRequest);
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

    @Operation(summary = "보존된 학습지 원본 다운로드", description = "업로드 당시 바이트를 변경 없이 반환합니다. 타 학원 자원은 404로 은닉합니다.")
    @GetMapping("/{worksheetId}/file")
    public ResponseEntity<Resource> getOriginalFile(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId)
            throws IOException {
        WorksheetFileResource file = worksheetService.getOriginalFile(principal.academyId(), worksheetId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .contentLength(file.resource().contentLength())
                .cacheControl(CacheControl.noStore())
                .eTag(file.sha256())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.resource());
    }

    @Operation(
            summary = "방향 보정 PNG 조회",
            description =
                    "전사·첨삭과 화면 표시의 공통 이미지입니다. width/height는 이 PNG의 픽셀 크기이며 regions는 전체 PNG 기준 0~1 정규화 값입니다. Bearer 인증 후 바이너리로 조회합니다.")
    @GetMapping("/{worksheetId}/pages/{pageNumber}/image")
    public ResponseEntity<byte[]> getPageImage(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("pageNumber") int pageNumber) {
        PreparedWorksheetImage image =
                worksheetService.getPreparedImage(principal.academyId(), worksheetId, pageNumber);
        byte[] png = image.png();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(png.length)
                .cacheControl(CacheControl.noStore())
                .eTag(image.sha256())
                .header("X-Content-Type-Options", "nosniff")
                .body(png);
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
            @Parameter(description = "교체할 새 학습지 파일", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) MultipartHttpServletRequest multipartRequest) {
        checkSingleImage(multipartRequest);
        WorksheetDetailResponse response =
                worksheetService.replaceWorksheetFile(principal.academyId(), worksheetId, ifMatch, file);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("학습지 파일이 교체되었습니다.", response));
    }

    private static void checkSingleImage(MultipartHttpServletRequest request) {
        if (request.getMultiFileMap().values().stream()
                        .mapToLong(files -> files.size())
                        .sum()
                != 1) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "한 요청에 이미지 한 장만 업로드할 수 있습니다.");
        }
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
