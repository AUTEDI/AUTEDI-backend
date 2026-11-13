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
            summary = "학습지 원본 파일(들) 업로드 (F02)",
            description =
                    "JPG/JPEG·PNG 원본과 EXIF 보정 PNG를 보존합니다. files/file/images의 다중 이미지는 순서대로 페이지가 됩니다. 실제 AI 생성은 현재 이미지 한 장만 지원하며 PDF는 지원하지 않습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description =
                        "업로드 성공. QR 로그인(휴대폰) 토큰이면 소유자 SSE로 worksheet.created {sessionId, worksheetId}가 전송되며, 첫 사진용 QR(worksheetId 없음)로만 새 작업을 만들 수 있고 QR 하나당 한 번만 가능합니다. 첨삭은 자동 시작되지 않습니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "FILE_001: 업로드할 파일이 비어 있습니다. / QR_007: QR 로그인(휴대폰) 토큰은 사진을 한 장만 올릴 수 있습니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "FILE_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"FILE_001\",\"message\":\"업로드할 파일이 비어 있습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "AUTH_001: 인증이 필요합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_001\",\"message\":\"인증이 필요합니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "QR_004: QR 로그인(휴대폰) 토큰에만 적용. 토큰 사용자와 QR 세션 소유자 불일치, 또는 QR 세션에 지정된 작업과 다른 대상",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_004",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_004\",\"message\":\"이 QR로 업로드할 수 있는 작업이 아닙니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "QR_006: QR 로그인(휴대폰) 토큰에만 적용. 이 QR로는 이미 사진을 올렸습니다(QR 하나당 한 번).",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_006",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_006\",\"message\":\"이 QR로는 이미 사진을 올렸습니다. 새 QR을 생성해 주세요.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "415",
                description = "FILE_002: 지원하지 않는 파일 형식",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "FILE_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"FILE_002\",\"message\":\"JPG/JPEG·PNG 이미지만 지원합니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "413",
                description = "FILE_005: 이미지 또는 보정 PNG가 크기 제한을 초과했습니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "FILE_005",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"FILE_005\",\"message\":\"이미지 또는 보정 PNG가 크기 제한을 초과했습니다.\",\"result\":null,\"error\":null}")))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WorksheetDetailResponse>> uploadWorksheet(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "업로드할 학습지 파일 목록 (다중 페이지 지원: files)", required = false)
                    @RequestParam(value = "files", required = false)
                    java.util.List<MultipartFile> files,
            @Parameter(description = "학습지 파일 (단일 또는 다중 업로드 호환: file)", required = false)
                    @RequestParam(value = "file", required = false)
                    java.util.List<MultipartFile> file,
            @Parameter(description = "업로드할 학습지 이미지 목록 (프론트엔드 다중 이미지 호환: images)", required = false)
                    @RequestParam(value = "images", required = false)
                    java.util.List<MultipartFile> images) {
        java.util.List<MultipartFile> targetFiles = new java.util.ArrayList<>();
        if (files != null) {
            targetFiles.addAll(
                    files.stream().filter(f -> f != null && !f.isEmpty()).toList());
        }
        if (file != null) {
            targetFiles.addAll(
                    file.stream().filter(f -> f != null && !f.isEmpty()).toList());
        }
        if (images != null) {
            targetFiles.addAll(
                    images.stream().filter(f -> f != null && !f.isEmpty()).toList());
        }

        if (targetFiles.isEmpty()) {
            throw new com.ceos24.autedi.global.exception.CustomException(
                    com.ceos24.autedi.global.exception.ErrorCode.EMPTY_FILE);
        }

        WorksheetDetailResponse response = worksheetService.uploadWorksheets(
                principal.userId(), principal.academyId(), principal.qrSessionId(), targetFiles);
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
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description =
                        "교체 성공. QR 로그인(휴대폰) 토큰이면 소유자 SSE로 worksheet.file_replaced {sessionId, worksheetId}가 전송됩니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "AUTH_001: 인증이 필요합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_001\",\"message\":\"인증이 필요합니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "QR_004: QR 로그인(휴대폰) 토큰에만 적용. 토큰 사용자와 QR 세션 소유자 불일치, 또는 QR 세션에 지정된 작업과 다른 대상",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "QR_004",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"QR_004\",\"message\":\"이 QR로 업로드할 수 있는 작업이 아닙니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "WORKSHEET_001: 학습지를 찾을 수 없거나 접근 권한이 없습니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "WORKSHEET_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"WORKSHEET_001\",\"message\":\"학습지를 찾을 수 없거나 접근 권한이 없습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description =
                        "WORKSHEET_002: DRAFT/FAILED 상태가 아니거나 다중 페이지 / QR_006: QR 로그인(휴대폰) 토큰에만 적용. 이 QR로는 이미 사진을 올렸습니다(QR 하나당 한 번).",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "WORKSHEET_002",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"WORKSHEET_002\",\"message\":\"현재 학습지 상태에서는 요청한 작업을 수행할 수 없습니다.\",\"result\":null,\"error\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "412",
                description = "COMMON_412: 데이터 버전이 변경되었습니다. 최신 데이터를 조회한 후 다시 시도해 주세요.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "COMMON_412",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"COMMON_412\",\"message\":\"데이터 버전이 변경되었습니다. 최신 데이터를 조회한 후 다시 시도해 주세요.\",\"result\":null,\"error\":null}")))
    })
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
        WorksheetDetailResponse response = worksheetService.replaceWorksheetFile(
                principal.academyId(), principal.userId(), principal.qrSessionId(), worksheetId, ifMatch, file);
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
