package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.correction.entity.RegionCoordinateUnit;
import com.ceos24.autedi.domain.student.dto.response.StudentSummaryResponse;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "학습지 상세 메타데이터 응답")
public record WorksheetDetailResponse(
        @Schema(description = "학습지 고유 ID", example = "1") Long id,
        @Schema(description = "조건부 갱신(If-Match)용 ETag", example = "\"ws_1-v1\"") String etag,
        @Schema(description = "AI 처리 상태 (DRAFT, PROCESSING, READY, FAILED)", example = "DRAFT") WorksheetStatus status,
        @Schema(description = "학습지 제목. 미지정이면 null", example = "11월 영어 작문") String title,
        @Schema(description = "연결된 학생 요약. 미지정이면 null") StudentSummaryResponse student,
        @Schema(description = "AI 처리 상태와 별개인 업무 저장 상태", example = "UNSAVED") WorksheetSaveStatus saveStatus,
        @Schema(description = "최초 업무 저장 시각. 미저장이면 null") LocalDateTime firstSavedAt,
        @Schema(description = "최근 업무 저장 시각. 미저장이면 null") LocalDateTime lastSavedAt,
        @Schema(description = "업무 최종 저장 시각. 미완료이면 null") LocalDateTime completedAt,
        @Schema(description = "원본 파일 상세 정보") FileDetailResponse file,
        @Schema(description = "페이지별 미리보기 목록") List<PageDetailResponse> pages,
        @Schema(description = "현재 설정된 첨삭 영역 목록") WorksheetSettingsResponse settings,
        @Schema(description = "가장 최근 AI 생성 작업 ID", example = "101") Long latestGenerationId,
        @Schema(description = "등록 일시", example = "2026-10-30T10:00:00") LocalDateTime createdAt,
        @Schema(description = "최종 수정 일시", example = "2026-10-30T10:05:00") LocalDateTime updatedAt) {

    public static WorksheetDetailResponse of(
            Worksheet worksheet, List<WorksheetPage> pages, List<WorksheetSetting> settings, Long latestGenerationId) {
        String mediaType =
                switch (worksheet.getFileType()) {
                    case JPG, JPEG -> "image/jpeg";
                    case PNG -> "image/png";
                    case PDF -> "application/pdf";
                };

        FileDetailResponse fileDetail = new FileDetailResponse(
                worksheet.getOriginalFilename(),
                mediaType,
                worksheet.getFileSize(),
                WorksheetFileUrls.original(worksheet.getFileUrl(), worksheet.getId()),
                null);

        List<PageDetailResponse> pageDetails = pages != null
                ? pages.stream()
                        .map(p -> new PageDetailResponse(
                                p.getId(),
                                p.getPageNumber(),
                                p.getWidth(),
                                p.getHeight(),
                                WorksheetFileUrls.image(p.getPreviewImageUrl(), worksheet.getId(), p.getPageNumber()),
                                null,
                                RegionCoordinateUnit.NORMALIZED_0_1))
                        .toList()
                : List.of();

        String settingsEtag = "\"settings_" + worksheet.getId() + "-v" + worksheet.getVersion() + "\"";
        WorksheetSettingsResponse settingsResponse = WorksheetSettingsResponse.of(settingsEtag, settings);

        return new WorksheetDetailResponse(
                worksheet.getId(),
                worksheet.getEtag(),
                worksheet.getStatus(),
                worksheet.getTitle(),
                worksheet.getStudent() == null ? null : StudentSummaryResponse.from(worksheet.getStudent()),
                worksheet.getSaveStatus(),
                worksheet.getFirstSavedAt(),
                worksheet.getLastSavedAt(),
                worksheet.getCompletedAt(),
                fileDetail,
                pageDetails,
                settingsResponse,
                latestGenerationId,
                worksheet.getCreatedAt(),
                worksheet.getUpdatedAt());
    }

    public static WorksheetDetailResponse of(
            Worksheet worksheet, List<WorksheetPage> pages, List<WorksheetSetting> settings) {
        return of(worksheet, pages, settings, null);
    }
}
